ALTER TABLE payments DROP CONSTRAINT payments_status_check;
ALTER TABLE payments ADD CONSTRAINT payments_status_check
    CHECK (status IN ('DRAFT','WAITING_PAYMENT','PAID','FAILED','REFUNDED'));
