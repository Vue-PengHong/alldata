
CREATE TABLE FILE_SOURCE (
    C_ID INT,
    C_BOOLEAN BOOLEAN,
    C_TINYINT TINYINT,
    C_SMALLINT SMALLINT,
    C_INT INT,
    C_BIGINT BIGINT,
    C_FLOAT FLOAT,
    C_DOUBLE DOUBLE,
    C_DECIMAL DECIMAL,
    C_STRING STRING,
    C_VARCHAR VARCHAR(255),
    C_CHAR CHAR(255),
    C_TIMESTAMP TIMESTAMP,
    C_DATE DATE
) WITH (
    'connector' = 'stream-x',
    'number-of-rows' = '10000'
);

CREATE TABLE SINK (
    C_ID INT,
    C_BOOLEAN BOOLEAN,
    C_TINYINT TINYINT,
    C_SMALLINT SMALLINT,
    C_INT INT,
    C_BIGINT BIGINT,
    C_FLOAT FLOAT,
    C_DOUBLE DOUBLE,
    C_DECIMAL DECIMAL,
    C_STRING STRING,
    C_VARCHAR VARCHAR(255),
    C_CHAR CHAR(255),
    C_TIMESTAMP TIMESTAMP,
    C_DATE DATE
) WITH (
    'connector' = 'ftp-x',
    'path' = '/data/sftp/start-chunjun/sql/sink',
    'protocol' = 'sftp',
    'host' = 'localhost',
    'username' = 'root',
    'password' = 'xxxxx',
    'format' = 'csv'
);

INSERT INTO SINK
SELECT *
FROM FILE_SOURCE;
