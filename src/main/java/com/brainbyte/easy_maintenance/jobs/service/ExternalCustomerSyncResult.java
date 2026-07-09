package com.brainbyte.easy_maintenance.jobs.service;

public record ExternalCustomerSyncResult(
    int totalFound,
    int success,
    int failure
) {
}
