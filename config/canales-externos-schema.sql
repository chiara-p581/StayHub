CREATE TABLE IF NOT EXISTS sincronizacion_procesada (
    solicitud_id varchar(64) PRIMARY KEY,
    fecha timestamp with time zone NOT NULL
);