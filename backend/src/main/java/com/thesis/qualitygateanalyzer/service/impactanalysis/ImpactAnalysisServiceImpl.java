package com.thesis.qualitygateanalyzer.service.impactanalysis;

import com.thesis.qualitygateanalyzer.domain.enums.ImpactTrend;
import com.thesis.qualitygateanalyzer.domain.enums.QualityGateTool;
import com.thesis.qualitygateanalyzer.domain.qualitygate.QGToolIntroduction;
import com.thesis.qualitygateanalyzer.domain.qualitygate.QualityGateHistoryDetection;
import com.thesis.qualitygateanalyzer.domain.qualitygate.RepositoryDetectionResult;
import com.thesis.qualitygateanalyzer.dto.response.ImpactAnalysisResponse;
import com.thesis.qualitygateanalyzer.dto.response.ImpactAnalysisSummaryDto;
import com.thesis.qualitygateanalyzer.dto.response.MetricComparisonDto;
import com.thesis.qualitygateanalyzer.dto.response.QualityMetricSnapshotDto;
import com.thesis.qualitygateanalyzer.entity.commit.CommitHistoryEntity;
import com.thesis.qualitygateanalyzer.entity.impactanalysis.ImpactAnalysisEntity;
import com.thesis.qualitygateanalyzer.entity.impactanalysis.ImpactComparisonEntity;
import com.thesis.qualitygateanalyzer.entity.impactanalysis.ImpactMetricsSnapshotEntity;
import com.thesis.qualitygateanalyzer.entity.impactanalysis.ImpactMetricsSnapshotEntity.Classification;
import com.thesis.qualitygateanalyzer.entity.impactanalysis.ImpactMetricsSnapshotEntity.DateSource;
import com.thesis.qualitygateanalyzer.exception.ImpactAnalysisNotFoundException;
import com.thesis.qualitygateanalyzer.mapper.ImpactAnalysisMapper;
import com.thesis.qualitygateanalyzer.repository.commit.CommitHistoryRepository;
import com.thesis.qualitygateanalyzer.repository.impactanalysis.ImpactAnalysisRepository;
import com.thesis.qualitygateanalyzer.service.github.GitHubApiClient;
import com.thesis.qualitygateanalyzer.service.qualitygate.PersistenceService;
import com.thesis.qualitygateanalyzer.service.qualitygate.QualityGateDetectionService;
import com.thesis.qualitygateanalyzer.service.qualitymetrics.QualityMetricsCsvLoader;
import com.thesis.qualitygateanalyzer.service.qualitymetrics.QualityMetricsIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Default {@link ImpactAnalysisService} implementation.
 * <p>
 * Reuses {@link QualityGateDetectionService}/{@link PersistenceService} for "does this repo
 * have a quality gate, and when was it introduced", {@link QualityMetricsIngestionService}
 * for the SonarQube metrics dataset, and {@link CommitHistoryRepository}/{@link GitHubApiClient}
 * to resolve a date for each metric snapshot (the dataset itself has no date column).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImpactAnalysisServiceImpl implements ImpactAnalysisService {

    /**
     * Which quality metrics feed the improvement score, and whether a decrease (issue/defect
     * counts) or an increase (coverage) counts as improvement. ncloc is tracked for chart
     * context only -- codebase size isn't a quality signal, so it never gets a trend of its own
     * and never contributes to the score. The three softwareQuality*Issues metrics are
     * SonarQube's newer "software quality" taxonomy re-labelling of bugs/codeSmells/vulnerabilities
     * (for the vast majority of rules they're numerically identical to their legacy counterpart) --
     * they still get their own trend for display, but are excluded from the score average so that
     * signal isn't counted twice.
     */
    private record MetricSpec(String name, Function<QualityMetricSnapshotDto, Double> extractor,
                               boolean higherIsBetter, boolean hasTrend, boolean includeInScore) {
    }

    private static final List<MetricSpec> METRIC_SPECS = List.of(
            new MetricSpec("bugs", d -> toDouble(d.getBugs()), false, true, true),
            new MetricSpec("vulnerabilities", d -> toDouble(d.getVulnerabilities()), false, true, true),
            new MetricSpec("codeSmells", d -> toDouble(d.getCodeSmells()), false, true, true),
            new MetricSpec("securityHotspots", d -> toDouble(d.getSecurityHotspots()), false, true, true),
            new MetricSpec("coverage", QualityMetricSnapshotDto::getCoverage, true, true, true),
            new MetricSpec("duplicatedLinesDensity", QualityMetricSnapshotDto::getDuplicatedLinesDensity, false, true, true),
            new MetricSpec("complexity", d -> toDouble(d.getComplexity()), false, true, true),
            new MetricSpec("cognitiveComplexity", d -> toDouble(d.getCognitiveComplexity()), false, true, true),
            new MetricSpec("softwareQualityReliabilityIssues", d -> toDouble(d.getSoftwareQualityReliabilityIssues()), false, true, false),
            new MetricSpec("softwareQualityMaintainabilityIssues", d -> toDouble(d.getSoftwareQualityMaintainabilityIssues()), false, true, false),
            new MetricSpec("softwareQualitySecurityIssues", d -> toDouble(d.getSoftwareQualitySecurityIssues()), false, true, false),
            new MetricSpec("ncloc", d -> toDouble(d.getNcloc()), true, false, false)
    );

    private record ResolvedRow(QualityMetricSnapshotDto source, Instant date, DateSource dateSource) {
    }

    private final QualityGateDetectionService detectionService;
    private final PersistenceService persistenceService;
    private final QualityMetricsIngestionService metricsIngestionService;
    private final QualityMetricsCsvLoader metricsCsvLoader;
    private final CommitHistoryRepository commitHistoryRepository;
    private final GitHubApiClient githubClient;
    private final ImpactAnalysisRepository impactAnalysisRepository;
    private final ImpactAnalysisMapper mapper;

    @Override
    @Transactional
    public ImpactAnalysisResponse analyze(String owner, String repo, boolean forceNewAnalysis) {
        if (!forceNewAnalysis) {
            Optional<ImpactAnalysisEntity> cached = impactAnalysisRepository.findByOwnerAndRepo(owner, repo);
            if (cached.isPresent()) {
                log.info("Returning cached impact analysis for {}/{}", owner, repo);
                return toResponse(cached.get(), true);
            }
        }

        Instant startTime = Instant.now();
        log.info("Computing impact analysis for {}/{} (forceNewAnalysis={})", owner, repo, forceNewAnalysis);

        RepositoryDetectionResult detection = resolveDetection(owner, repo);
        if (!detection.isHasQualityGate()) {
            log.info("No quality gate detected for {}/{}; nothing to analyze", owner, repo);
            clearStaleImpactAnalysisIfPresent(owner, repo);
            return noQualityGateResponse(owner, repo, detection.getUrl());
        }

        QualityGateHistoryDetection history = detection.getQualityGateHistory();
        List<QGToolIntroduction> introductions = history == null ? List.of() : history.getToolIntroductions();
        List<QGToolIntroduction> datedIntroductions = introductions == null ? List.of() : introductions.stream()
                .filter(i -> i.getEffectiveDate() != null)
                .toList();

        if (datedIntroductions.isEmpty()) {
            log.info("Quality gate detected for {}/{} but no dated tool introductions available", owner, repo);
            return persistInsufficientData(owner, repo, detection.getUrl(), startTime);
        }

        List<QualityMetricSnapshotDto> metricRows = loadMetricRows(owner, repo, forceNewAnalysis);
        if (metricRows.isEmpty()) {
            log.info("No quality metrics dataset rows for {}/{}", owner, repo);
            return persistInsufficientData(owner, repo, detection.getUrl(), startTime);
        }

        List<ResolvedRow> resolvedRows = resolveDates(owner, repo, metricRows);
        if (resolvedRows.isEmpty()) {
            log.info("Could not resolve a date for any quality metric snapshot for {}/{}", owner, repo);
            return persistInsufficientData(owner, repo, detection.getUrl(), startTime);
        }

        if (forceNewAnalysis && impactAnalysisRepository.existsByOwnerAndRepo(owner, repo)) {
            log.info("Force refresh: clearing existing impact analysis for {}/{}", owner, repo);
            impactAnalysisRepository.deleteByOwnerAndRepo(owner, repo);
        }

        ImpactAnalysisEntity entity = buildAnalysis(owner, repo, detection.getUrl(), datedIntroductions, resolvedRows, startTime);
        impactAnalysisRepository.save(entity);
        log.info("Impact analysis complete for {}/{}: {} tool(s), overall trend {}",
                owner, repo, entity.getToolsAnalyzedCount(), entity.getOverallTrend());

        return toResponse(entity, false);
    }

    @Override
    @Transactional(readOnly = true)
    public ImpactAnalysisResponse getAnalysis(String owner, String repo) {
        ImpactAnalysisEntity entity = impactAnalysisRepository.findByOwnerAndRepo(owner, repo)
                .orElseThrow(() -> new ImpactAnalysisNotFoundException(
                        "No impact analysis found for " + owner + "/" + repo));
        return toResponse(entity, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ImpactAnalysisSummaryDto> listAll() {
        return impactAnalysisRepository.findByOrderByComputedAtDesc().stream()
                .map(e -> ImpactAnalysisSummaryDto.builder()
                        .owner(e.getOwner())
                        .repo(e.getRepo())
                        .repositoryUrl(e.getRepositoryUrl())
                        .toolsAnalyzed(e.getToolsAnalyzedCount() != null ? e.getToolsAnalyzedCount() : 0)
                        .overallImprovementScore(e.getOverallImprovementScore())
                        .overallTrend(e.getOverallTrend())
                        .computedAt(e.getComputedAt())
                        .build())
                .toList();
    }

    /**
     * Forces a fresh detection, then (only if a quality gate was actually found -- otherwise
     * there's nothing for commits/metrics to serve) a forced commit-history refresh and a
     * forced quality-metrics re-ingestion. Deliberately does not touch {@code t_impact_analysis}
     * itself: recomputing the before/after comparison from this refreshed data is a separate,
     * deliberate step via {@link #analyze} with {@code forceNewAnalysis=true}.
     * <p>
     * Unlike {@link #resolveDetection}, this never reuses a cached detection -- forcing a fresh
     * one is the entire point of this method.
     */
    @Override
    @Transactional
    public RepositoryDetectionResult refreshRepositoryData(String owner, String repo) {
        log.info("Refreshing underlying data for {}/{} (forced detection + commits + metrics)", owner, repo);

        RepositoryDetectionResult fresh = detectionService.detect("https://github.com/" + owner + "/" + repo);
        persistenceService.saveDetection(fresh, true);

        if (fresh.isHasQualityGate()) {
            resolveCommitDatesFromHistory(owner, repo, true);
            loadMetricRows(owner, repo, true);
        } else {
            log.info("No quality gate detected for {}/{}; skipping commit/metrics refresh", owner, repo);
            clearStaleImpactAnalysisIfPresent(owner, repo);
        }

        log.info("Data refresh complete for {}/{} ({} API calls made)", owner, repo, githubClient.getApiCallCount());
        return fresh;
    }

    /**
     * Deletes any stored impact analysis for a repository that's just been found to no longer
     * have a detected quality gate (tool removed, or detection re-run with a different result).
     * Without this, a stale, now-inaccurate {@code t_impact_analysis} row from when the repo did
     * have a quality gate would keep being served by {@code getAnalysis}/{@code listAll} forever
     * -- there's no other endpoint that can remove it.
     */
    private void clearStaleImpactAnalysisIfPresent(String owner, String repo) {
        if (impactAnalysisRepository.existsByOwnerAndRepo(owner, repo)) {
            log.info("Quality gate no longer detected for {}/{}; clearing stale impact analysis", owner, repo);
            impactAnalysisRepository.deleteByOwnerAndRepo(owner, repo);
        }
    }

    // DETECTION (reuses QualityGateDetectionService / PersistenceService as-is)

    /**
     * Reuses the cached quality gate detection if one exists; otherwise runs and persists a
     * fresh one via the existing detection pipeline. Deliberately never forces a fresh
     * detection itself -- this feature's forceNewAnalysis recomputes the impact analysis,
     * not the (separately cacheable/forceable) quality gate detection.
     */
    private RepositoryDetectionResult resolveDetection(String owner, String repo) {
        Optional<RepositoryDetectionResult> cached = persistenceService.loadDetection(owner, repo);
        if (cached.isPresent()) {
            return cached.get();
        }

        log.info("No prior quality gate detection for {}/{}; running detection now", owner, repo);
        RepositoryDetectionResult fresh = detectionService.detect("https://github.com/" + owner + "/" + repo);
        persistenceService.saveDetection(fresh, false);
        return fresh;
    }

    // QUALITY METRICS (reuses QualityMetricsIngestionService as-is)

    /**
     * Pre-checks with the (non-transactional) CSV loader directly before calling
     * {@code ingest()}, rather than catching {@link com.thesis.qualitygateanalyzer.exception.NoDataInDatasetException}
     * from it. {@code ingest()} runs {@code @Transactional} and, since it's called from within
     * this method's own transaction, joining it (default propagation), any exception it throws
     * marks that whole shared transaction rollback-only the instant it's thrown -- catching it
     * here would not undo that, and {@code analyze()} returning normally afterward would then
     * fail at commit with {@code UnexpectedRollbackException}. Guaranteeing {@code ingest()}
     * is only called when it cannot hit its throwing branch avoids that entirely.
     * <p>
     * {@code forceNewAnalysis} is forwarded to {@code ingest()} so a forced impact-analysis
     * recompute also re-reads the CSV dataset instead of silently reusing whatever metrics
     * happen to already be stored for this repo (e.g. from a stale prior ingestion). Because
     * a forced call can now reach {@code ingest()}'s CSV-loading/throwing branch even when
     * metrics are already stored, the empty-dataset pre-check below has to account for that
     * case too, not just the "nothing stored yet" case.
     */
    private List<QualityMetricSnapshotDto> loadMetricRows(String owner, String repo, boolean forceNewAnalysis) {
        boolean willReachCsv = forceNewAnalysis || !metricsIngestionService.hasStoredMetrics(owner, repo);
        if (willReachCsv && metricsCsvLoader.loadRowsForRepository(owner, repo).isEmpty()) {
            return List.of();
        }
        return metricsIngestionService.ingest(owner, repo, forceNewAnalysis).response().getRecords();
    }

    // DATE RESOLUTION (reuses CommitHistoryRepository/CommitHistoryEntity + GitHubApiClient.getPullRequest)

    private List<ResolvedRow> resolveDates(String owner, String repo, List<QualityMetricSnapshotDto> rows) {
        // Never forces a commit-history refetch here: analyze()'s forceNewAnalysis recomputes
        // the comparison from whatever data is already stored, same as it deliberately never
        // forces a fresh detection either (see resolveDetection). Forcing fresh commits is a
        // separate, explicit action via refreshRepositoryData().
        Map<String, Instant> commitDates = resolveCommitDatesFromHistory(owner, repo, false);
        Map<Integer, Map<String, Object>> prCache = new HashMap<>();

        List<ResolvedRow> resolved = new ArrayList<>();
        for (QualityMetricSnapshotDto row : rows) {
            ResolvedRow r = resolveDate(owner, repo, row, commitDates, prCache);
            if (r != null) {
                resolved.add(r);
            } else {
                log.debug("Could not resolve a date for PR #{} ({}) in {}/{}",
                        row.getPullRequestNumber(), row.getAnalysisRole(), owner, repo);
            }
        }
        return resolved;
    }

    private ResolvedRow resolveDate(String owner, String repo, QualityMetricSnapshotDto row,
                                     Map<String, Instant> commitDates, Map<Integer, Map<String, Object>> prCache) {
        String sha = row.getCommitHash();
        if (sha != null) {
            Instant fromHistory = commitDates.get(sha);
            if (fromHistory != null) {
                return new ResolvedRow(row, fromHistory, DateSource.COMMIT_HISTORY);
            }
        }

        if (row.getPullRequestNumber() == null) {
            return null;
        }

        Map<String, Object> pr = prCache.computeIfAbsent(row.getPullRequestNumber(),
                n -> githubClient.getPullRequest(owner, repo, n).orElse(null));
        if (pr == null) {
            return null;
        }

        boolean isBaseRow = "pr_base".equalsIgnoreCase(row.getAnalysisRole());
        String dateStr = isBaseRow
                ? (String) pr.get("created_at")
                : firstNonNull((String) pr.get("merged_at"), (String) pr.get("closed_at"));

        Instant date = parseInstant(dateStr);
        return date == null ? null : new ResolvedRow(row, date, DateSource.PR_METADATA);
    }

    /**
     * Ensures the repository's default-branch commit history is cached, reusing it if
     * {@code CommitChunkService} has already ingested it (unless {@code forceRefresh} is set,
     * matching that service's own force semantics). Duplicates {@code CommitChunkServiceImpl}'s
     * fetch-and-cache logic (which is private there) rather than changing that service's public
     * contract.
     */
    private Map<String, Instant> resolveCommitDatesFromHistory(String owner, String repo, boolean forceRefresh) {
        List<CommitHistoryEntity> commits;
        if (!forceRefresh && commitHistoryRepository.existsByOwnerAndRepo(owner, repo)) {
            commits = commitHistoryRepository.findByOwnerAndRepoOrderByCommitDateAsc(owner, repo);
        } else {
            if (forceRefresh) {
                log.info("Force refresh: clearing cached commit history for {}/{}", owner, repo);
                commitHistoryRepository.deleteByOwnerAndRepo(owner, repo);
            }
            log.info("Fetching full commit history for {}/{} from GitHub to resolve snapshot dates", owner, repo);
            List<Map<String, Object>> raw = githubClient.getAllCommits(owner, repo);
            Collections.reverse(raw);
            commits = raw.stream()
                    .map(c -> toCommitHistoryEntity(owner, repo, c))
                    .filter(Objects::nonNull)
                    .toList();
            if (!commits.isEmpty()) {
                commitHistoryRepository.saveAll(commits);
            }
        }
        return commits.stream().collect(Collectors.toMap(
                CommitHistoryEntity::getCommitSha, CommitHistoryEntity::getCommitDate, (a, b) -> a));
    }

    @SuppressWarnings("unchecked")
    private CommitHistoryEntity toCommitHistoryEntity(String owner, String repo, Map<String, Object> raw) {
        try {
            String sha = (String) raw.get("sha");
            Map<String, Object> commit = (Map<String, Object>) raw.get("commit");
            Map<String, Object> committer = (Map<String, Object>) commit.get("committer");
            Instant commitDate = Instant.parse((String) committer.get("date"));
            return CommitHistoryEntity.builder().owner(owner).repo(repo).commitSha(sha).commitDate(commitDate).build();
        } catch (Exception e) {
            log.warn("Skipping unparseable commit entry: {}", e.getMessage());
            return null;
        }
    }

    // CLASSIFICATION + COMPUTATION

    private ImpactAnalysisEntity buildAnalysis(String owner, String repo, String repositoryUrl,
                                                List<QGToolIntroduction> introductions,
                                                List<ResolvedRow> resolvedRows, Instant startTime) {
        ImpactAnalysisEntity analysis = ImpactAnalysisEntity.builder()
                .owner(owner)
                .repo(repo)
                .repositoryUrl(repositoryUrl)
                .build();

        List<ImpactComparisonEntity> comparisons = new ArrayList<>();
        int totalBefore = 0;
        int totalAfter = 0;

        for (QGToolIntroduction introduction : introductions) {
            List<ResolvedRow> before = resolvedRows.stream()
                    .filter(r -> r.date().isBefore(introduction.getEffectiveDate()))
                    .toList();
            List<ResolvedRow> after = resolvedRows.stream()
                    .filter(r -> !r.date().isBefore(introduction.getEffectiveDate()))
                    .toList();

            before.forEach(r -> analysis.addSnapshot(toSnapshotEntity(r, introduction.getTool(), Classification.BEFORE)));
            after.forEach(r -> analysis.addSnapshot(toSnapshotEntity(r, introduction.getTool(), Classification.AFTER)));
            totalBefore += before.size();
            totalAfter += after.size();

            ImpactComparisonEntity comparison = buildComparison(introduction, before, after);
            analysis.addComparison(comparison);
            comparisons.add(comparison);
        }

        applyOverallStats(analysis, comparisons);

        analysis.setToolsAnalyzedCount(introductions.size());
        analysis.setTotalSnapshotsBefore(totalBefore);
        analysis.setTotalSnapshotsAfter(totalAfter);
        resolvedRows.stream().map(ResolvedRow::date).min(Instant::compareTo).ifPresent(analysis::setDateRangeStart);
        resolvedRows.stream().map(ResolvedRow::date).max(Instant::compareTo).ifPresent(analysis::setDateRangeEnd);
        analysis.setAnalysisTimeMs(Instant.now().toEpochMilli() - startTime.toEpochMilli());
        analysis.setApiCallsMade(githubClient.getApiCallCount());
        analysis.setComputedAt(Instant.now());

        return analysis;
    }

    private ImpactMetricsSnapshotEntity toSnapshotEntity(ResolvedRow row, QualityGateTool tool,
                                                           Classification classification) {
        QualityMetricSnapshotDto d = row.source();
        return ImpactMetricsSnapshotEntity.builder()
                .tool(tool)
                .classification(classification)
                .pullRequestNumber(d.getPullRequestNumber())
                .analysisRole(d.getAnalysisRole())
                .commitSha(d.getCommitHash())
                .commitDate(row.date())
                .dateSource(row.dateSource())
                .bugs(d.getBugs())
                .vulnerabilities(d.getVulnerabilities())
                .codeSmells(d.getCodeSmells())
                .securityHotspots(d.getSecurityHotspots())
                .coverage(d.getCoverage())
                .duplicatedLinesDensity(d.getDuplicatedLinesDensity())
                .ncloc(d.getNcloc())
                .complexity(d.getComplexity())
                .cognitiveComplexity(d.getCognitiveComplexity())
                .softwareQualityReliabilityIssues(d.getSoftwareQualityReliabilityIssues())
                .softwareQualityMaintainabilityIssues(d.getSoftwareQualityMaintainabilityIssues())
                .softwareQualitySecurityIssues(d.getSoftwareQualitySecurityIssues())
                .build();
    }

    private ImpactComparisonEntity buildComparison(QGToolIntroduction introduction, List<ResolvedRow> before, List<ResolvedRow> after) {
        List<ResolvedRow> validBefore = before.stream().filter(ImpactAnalysisServiceImpl::hasRealMetrics).toList();
        List<ResolvedRow> validAfter = after.stream().filter(ImpactAnalysisServiceImpl::hasRealMetrics).toList();

        Map<String, MetricComparisonDto> metrics = new LinkedHashMap<>();
        List<Double> normalizedDeltas = new ArrayList<>();

        for (MetricSpec spec : METRIC_SPECS) {
            List<Double> beforeValues = validBefore.stream().map(r -> spec.extractor().apply(r.source())).filter(Objects::nonNull).toList();
            List<Double> afterValues = validAfter.stream().map(r -> spec.extractor().apply(r.source())).filter(Objects::nonNull).toList();

            Double avgBefore = average(beforeValues);
            Double avgAfter = average(afterValues);
            Double deltaAbsolute = (avgBefore != null && avgAfter != null) ? avgAfter - avgBefore : null;
            Double deltaPercent = (deltaAbsolute != null && avgBefore != 0)
                    ? (deltaAbsolute / Math.abs(avgBefore)) * 100
                    : null;

            ImpactTrend metricTrend = null;
            if (spec.hasTrend() && deltaPercent != null) {
                double normalized = spec.higherIsBetter() ? deltaPercent : -deltaPercent;
                metricTrend = ImpactTrend.fromDeltaPercent(normalized);
                if (spec.includeInScore()) {
                    normalizedDeltas.add(normalized);
                }
            }

            metrics.put(spec.name(), MetricComparisonDto.builder()
                    .avgBefore(avgBefore)
                    .avgAfter(avgAfter)
                    .medianBefore(median(beforeValues))
                    .medianAfter(median(afterValues))
                    .deltaAbsolute(deltaAbsolute)
                    .deltaPercent(deltaPercent)
                    .trend(metricTrend)
                    .build());
        }

        boolean hasBothSides = !validBefore.isEmpty() && !validAfter.isEmpty();
        Double improvementScore = (hasBothSides && !normalizedDeltas.isEmpty())
                ? normalizedDeltas.stream().mapToDouble(Double::doubleValue).average().orElse(0)
                : null;
        ImpactTrend trend = improvementScore != null ? ImpactTrend.fromDeltaPercent(improvementScore) : ImpactTrend.INSUFFICIENT_DATA;

        return ImpactComparisonEntity.builder()
                .tool(introduction.getTool())
                .toolCategory(introduction.getCategory())
                .introducedAt(introduction.getEffectiveDate())
                .introducedCommitSha(introduction.getEffectiveIntroductionCommit() != null
                        ? introduction.getEffectiveIntroductionCommit().getSha() : null)
                .samplesBefore(validBefore.size())
                .samplesAfter(validAfter.size())
                .improvementScore(improvementScore)
                .trend(trend)
                .metricsComparison(metrics)
                .build();
    }

    /**
     * A snapshot row with {@code ncloc == 0} means SonarQube scanned zero lines of code for
     * that PR (an empty/placeholder analysis, not a codebase that shrank to nothing) -- every
     * other metric on such a row is a spurious zero, not a real measurement. Rows like this
     * left unfiltered can flood one side of the before/after comparison (observed for real on
     * apache/cordova-browser and apache/datasketches-hive) and produce a misleadingly perfect
     * "100% improved" score. They're still kept in the persisted timeline for transparency --
     * only excluded from the comparison math.
     */
    private static boolean hasRealMetrics(ResolvedRow row) {
        Integer ncloc = row.source().getNcloc();
        return ncloc != null && ncloc > 0;
    }

    private void applyOverallStats(ImpactAnalysisEntity analysis, List<ImpactComparisonEntity> comparisons) {
        List<Double> scores = comparisons.stream()
                .map(ImpactComparisonEntity::getImprovementScore)
                .filter(Objects::nonNull)
                .toList();

        Double overallScore = scores.isEmpty() ? null : scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        analysis.setOverallImprovementScore(overallScore);
        analysis.setOverallTrend(overallScore != null ? ImpactTrend.fromDeltaPercent(overallScore) : ImpactTrend.INSUFFICIENT_DATA);
    }

    // RESPONSE ASSEMBLY

    private ImpactAnalysisResponse toResponse(ImpactAnalysisEntity entity, boolean cached) {
        return ImpactAnalysisResponse.builder()
                .owner(entity.getOwner())
                .repo(entity.getRepo())
                .repositoryUrl(entity.getRepositoryUrl())
                .hasQualityGate(true)
                .toolsAnalyzed(entity.getToolsAnalyzedCount() != null ? entity.getToolsAnalyzedCount() : 0)
                .overallImprovementScore(entity.getOverallImprovementScore())
                .overallTrend(entity.getOverallTrend())
                .dateRangeStart(entity.getDateRangeStart())
                .dateRangeEnd(entity.getDateRangeEnd())
                .computedAt(entity.getComputedAt())
                .cached(cached)
                .comparisons(mapper.toComparisonDtos(entity.getComparisons()))
                .timeline(mapper.toTimelineDtos(entity.getSnapshots()))
                .build();
    }

    private ImpactAnalysisResponse noQualityGateResponse(String owner, String repo, String repositoryUrl) {
        return ImpactAnalysisResponse.builder()
                .owner(owner)
                .repo(repo)
                .repositoryUrl(repositoryUrl)
                .hasQualityGate(false)
                .toolsAnalyzed(0)
                .overallTrend(ImpactTrend.INSUFFICIENT_DATA)
                .cached(false)
                .comparisons(List.of())
                .timeline(List.of())
                .build();
    }

    /**
     * Persists a minimal record for a repository that has a detected quality gate but
     * nothing comparable (no dated introductions, no metrics dataset rows, or no snapshot
     * dates could be resolved), so a repeat call doesn't redo the expensive detection/dataset
     * work every time.
     */
    private ImpactAnalysisResponse persistInsufficientData(String owner, String repo, String repositoryUrl, Instant startTime) {
        clearStaleImpactAnalysisIfPresent(owner, repo);

        ImpactAnalysisEntity entity = ImpactAnalysisEntity.builder()
                .owner(owner)
                .repo(repo)
                .repositoryUrl(repositoryUrl)
                .toolsAnalyzedCount(0)
                .overallTrend(ImpactTrend.INSUFFICIENT_DATA)
                .totalSnapshotsBefore(0)
                .totalSnapshotsAfter(0)
                .analysisTimeMs(Instant.now().toEpochMilli() - startTime.toEpochMilli())
                .apiCallsMade(githubClient.getApiCallCount())
                .computedAt(Instant.now())
                .build();
        impactAnalysisRepository.save(entity);

        return toResponse(entity, false);
    }

    // HELPERS

    private static Double toDouble(Integer i) {
        return i == null ? null : i.doubleValue();
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    private static Instant parseInstant(String s) {
        if (s == null) return null;
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static Double average(List<Double> values) {
        return values.isEmpty() ? null : values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private static Double median(List<Double> values) {
        if (values.isEmpty()) return null;
        List<Double> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }
}
