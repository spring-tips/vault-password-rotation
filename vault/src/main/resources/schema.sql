set time zone 'UTC';
create extension if not exists pgcrypto;

CREATE TABLE if not exists payments
(
    id              VARCHAR(255) PRIMARY KEY NOT NULL,
    name            VARCHAR(255)             NOT NULL,
    billing_address VARCHAR(255)             NOT NULL,
    created_at      TIMESTAMP                NOT NULL
);

delete from payments ;
insert into payments (id, name, billing_address, created_at)
values ('1', 'test', '123 Street St', NOW()) ;