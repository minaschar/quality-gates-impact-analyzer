-- V2__initial_data.sql
-- Quality Gate Analyzer - Initial Configuration Data

-- API configuration

INSERT INTO t_configuration (config_key, config_value, data_type, category, description)
VALUES ('GITHUB_TOKEN',
        '',
        'STRING',
        'API',
        'GitHub Personal Access Token. Without token: 60 requests/hour. With token: 5000 requests/hour.');

-- Limits configuration

INSERT INTO t_configuration (config_key, config_value, data_type, category, description)
VALUES ('SAMPLE_PRS_LIMIT',
        '100',
        'INTEGER',
        'LIMITS',
        'Maximum number of PRs to analyze for enforcement verification.'),
       ('WORKFLOW_RUNS_LIMIT',
        '100',
        'INTEGER',
        'LIMITS',
        'Maximum number of workflow runs to fetch per repository.'),
       ('PR_ANALYSIS_LIMIT',
        '100',
        'INTEGER',
        'LIMITS',
        'Maximum number of PRs to analyze in detail.'),
       ('CHECK_RUNS_PER_COMMIT',
        '100',
        'INTEGER',
        'LIMITS',
        'Maximum number of check runs to fetch per commit.'),
       ('MAX_BINARY_SEARCH_ITERATIONS',
        '50',
        'INTEGER',
        'LIMITS',
        'Maximum iterations for PR binary search when finding tool introduction.'),
       ('LINEAR_SEARCH_THRESHOLD',
        '10',
        'INTEGER',
        'LIMITS',
        'Switch from binary to linear search when PR range is below this threshold.'),
       ('SAMPLE_PRS_TO_RETURN',
        '5',
        'INTEGER',
        'LIMITS',
        'Number of sample PRs to include in the response for verification.');

-- Features configuration

INSERT INTO t_configuration (config_key, config_value, data_type, category, description)
VALUES ('ENABLE_PR_FALLBACK',
        'true',
        'BOOLEAN',
        'FEATURES',
        'Enable PR-based binary search fallback when Git Blame fails to find tool introduction.'),
       ('ENABLE_HISTORY_ANALYSIS',
        'true',
        'BOOLEAN',
        'FEATURES',
        'Enable history analysis to find when quality gate tools were introduced.'),
       ('ENABLE_EXTERNAL_CHECK_DETECTION',
        'true',
        'BOOLEAN',
        'FEATURES',
        'Enable detection of quality gate failures from external apps (SonarCloud, Codecov, etc.).');
