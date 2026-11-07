-- Run ONCE, after changing Opportunity.experience from String to Integer. Adjust table/column names to your schema.
-- Old text -> minimum years: 'fresher' -> 0, '6 months' -> 0, '1 year' -> 1, '3-5 years' -> 3, '5+ years' -> 5, anything else -> NULL.

-- PostgreSQL
ALTER TABLE opportunity ALTER COLUMN experience TYPE integer USING (
  CASE WHEN experience IS NULL THEN NULL
       WHEN experience ~* 'fresher' THEN 0
       WHEN experience ~* 'month' THEN substring(experience from '^[0-9]+')::int / 12
       WHEN experience ~ '^[0-9]+' THEN substring(experience from '^[0-9]+')::int
       ELSE NULL END);

-- MySQL 8 (use instead of the PostgreSQL block)
-- UPDATE opportunity SET experience = CASE
--   WHEN experience IS NULL THEN NULL
--   WHEN experience LIKE '%fresher%' THEN '0'
--   WHEN experience LIKE '%month%' THEN CAST(FLOOR(CAST(REGEXP_SUBSTR(experience, '^[0-9]+') AS UNSIGNED) / 12) AS CHAR)
--   WHEN experience REGEXP '^[0-9]+' THEN REGEXP_SUBSTR(experience, '^[0-9]+')
--   ELSE NULL END;
-- ALTER TABLE opportunity MODIFY experience INT NULL;
