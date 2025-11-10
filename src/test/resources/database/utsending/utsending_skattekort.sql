INSERT INTO utsending (id, abonnement_id, fnr, inntektsaar, forsystem, opprettet)
VALUES (1, 5001, '12345678901', 2024, 'OS', '2024-12-15T08:30:00Z'),
       (2, 5001, '10987654321', 2025, 'OS', '2025-01-10T09:15:00Z'),
       (3, 5003, '34567890123', 2026, 'OS', '2025-12-20T07:45:00Z');

SELECT setval('utsending_id_seq', (SELECT coalesce(max(id), 0) FROM utsending) + 1, false);