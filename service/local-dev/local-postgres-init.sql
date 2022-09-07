CREATE DATABASE wsm_db;
CREATE ROLE dbuser WITH LOGIN ENCRYPTED PASSWORD 'dbpwd';
CREATE DATABASE wsm_stairway;
CREATE ROLE stairwayuser WITH LOGIN ENCRYPTED PASSWORD 'stairwaypwd';
CREATE DATABASE policy_db;
CREATE DATABASE landingzone_db;
CREATE ROLE landingzoneuser WITH LOGIN ENCRYPTED PASSWORD 'landingzonepwd';
CREATE DATABASE landingzone_stairway_db;
CREATE ROLE landingzonestairwayuser WITH LOGIN ENCRYPTED PASSWORD 'landingzonestairwaypwd';