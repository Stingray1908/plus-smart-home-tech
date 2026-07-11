-- Сначала создаём хабы — на них ссылаются датчики и сценарии
CREATE TABLE IF NOT EXISTS hubs (
    hub_id VARCHAR(64) PRIMARY KEY,
    location VARCHAR(255)
);

-- создаём таблицу scenarios
CREATE TABLE IF NOT EXISTS scenarios (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    hub_id VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    UNIQUE(hub_id, name),
    FOREIGN KEY (hub_id) REFERENCES hubs(hub_id) ON DELETE CASCADE
);

-- создаём таблицу sensors
CREATE TABLE IF NOT EXISTS sensors (
    id VARCHAR(64) PRIMARY KEY,
    hub_id VARCHAR(64) NOT NULL,
    FOREIGN KEY (hub_id) REFERENCES hubs(hub_id) ON DELETE CASCADE
);

-- создаём таблицу conditions
CREATE TABLE IF NOT EXISTS conditions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    operation VARCHAR(50) NOT NULL,
    value INTEGER
);

-- создаём таблицу actions
CREATE TABLE IF NOT EXISTS actions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    value INTEGER
);

-- создаём таблицу scenario_conditions
CREATE TABLE IF NOT EXISTS scenario_conditions (
    scenario_id BIGINT NOT NULL,
    sensor_id VARCHAR(64) NOT NULL,
    condition_id BIGINT NOT NULL,
    PRIMARY KEY (scenario_id, sensor_id, condition_id),
    FOREIGN KEY (scenario_id) REFERENCES scenarios(id) ON DELETE CASCADE,
    FOREIGN KEY (sensor_id)   REFERENCES sensors(id)   ON DELETE CASCADE,
    FOREIGN KEY (condition_id)REFERENCES conditions(id) ON DELETE CASCADE
);

-- создаём таблицу scenario_actions
CREATE TABLE IF NOT EXISTS scenario_actions (
    scenario_id BIGINT NOT NULL,
    sensor_id VARCHAR(64) NOT NULL,
    action_id BIGINT NOT NULL,
    PRIMARY KEY (scenario_id, sensor_id, action_id),
    FOREIGN KEY (scenario_id) REFERENCES scenarios(id) ON DELETE CASCADE,
    FOREIGN KEY (sensor_id)   REFERENCES sensors(id)   ON DELETE CASCADE,
    FOREIGN KEY (action_id)  REFERENCES actions(id)    ON DELETE CASCADE
);

-- функция проверки, что сценарий и датчик принадлежат одному хабу
CREATE OR REPLACE FUNCTION check_hub_id()
RETURNS TRIGGER AS

'
BEGIN
    IF (SELECT hub_id FROM scenarios WHERE id = NEW.scenario_id) != (SELECT hub_id FROM sensors WHERE id = NEW.sensor_id) THEN
        RAISE EXCEPTION ''Hub IDs do not match for scenario_id % and sensor_id %'', NEW.scenario_id, NEW.sensor_id;
    END IF;
    RETURN NEW;
END;
'
LANGUAGE plpgsql;

-- триггер для scenario_conditions
CREATE OR REPLACE TRIGGER tr_bi_scenario_conditions_hub_id_check
BEFORE INSERT ON scenario_conditions
FOR EACH ROW
EXECUTE FUNCTION check_hub_id();

-- триггер для scenario_actions
CREATE OR REPLACE TRIGGER tr_bi_scenario_actions_hub_id_check
BEFORE INSERT ON scenario_actions
FOR EACH ROW
EXECUTE FUNCTION check_hub_id();
