create sequence account_number_seq as integer minvalue 1 maxvalue 999999 no cycle;

create table savings_account (
    id                    uuid         primary key,
    bank                  char(2)      not null,
    branch                char(4)      not null,
    account_base          char(7)      not null,
    suffix                smallint     not null,
    customer_id           uuid         not null,
    customer_name         varchar(400) not null,
    customer_account_seq  smallint     not null,
    nickname              varchar(120),
    idempotency_key       uuid,
    request_fingerprint   char(64),
    created_at            timestamptz  not null,

    constraint savings_account_number_uk     unique (bank, branch, account_base, suffix),
    constraint savings_account_customer_uk   unique (customer_id, customer_account_seq),
    constraint savings_account_idempotent_uk unique (customer_id, idempotency_key),
    constraint savings_account_seq_ck        check (customer_account_seq between 1 and 5),
    constraint savings_account_nickname_ck
        check (nickname is null or char_length(nickname) between 5 and 30),
    constraint savings_account_bank_ck       check (bank ~ '^[0-9]{2}$'),
    constraint savings_account_branch_ck     check (branch ~ '^[0-9]{4}$'),
    constraint savings_account_base_ck       check (account_base ~ '^[0-9]{7}$'),
    constraint savings_account_suffix_ck     check (suffix between 0 and 999),
    constraint savings_account_idempotency_ck
        check ((idempotency_key is null) = (request_fingerprint is null))
);

grant usage on schema westpac to westpac_app;
grant select, insert on savings_account to westpac_app;
grant usage on sequence account_number_seq to westpac_app;
