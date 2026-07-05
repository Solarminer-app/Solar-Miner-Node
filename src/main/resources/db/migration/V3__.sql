ALTER TABLE pv_sites
    ADD COLUMN timezone_id VARCHAR(255);

ALTER TABLE miners
    ADD COLUMN power_step_size_watts INTEGER,
    ADD COLUMN min_run_time_minutes INTEGER,
    ADD COLUMN min_idle_time_minutes INTEGER;