import { NonRetryableError } from '@agricontract/eventing';
import {
  activationFailedBody,
  activationFailedCommand,
  activationFailedPayload,
  anchorBody,
  anchorCommand,
  anchorEnvelope,
  anchorPayload,
  cancelBody,
  cancelCommand,
  cancelEnvelope,
  cancelPayload,
  KYC_EMAIL,
  kycBody,
  kycCommand,
  kycPayload,
  kycVerifiedPayload,
  BREACH_TERMINATION,
  terminatedBody,
  terminatedCommand,
  terminatedPayload,
} from '../../../test/support/commands';
import {
  parseActivationCancelRequested,
  CONTRACT_ACTIVATION_FAILED_FIELDS,
  parseContractActivationFailedRequested,
  CONTRACT_TERMINATED_FIELDS,
  parseContractAnchorRequested,
  parseContractTerminatedRequested,
  parseUserKycResultRequested,
  USER_KYC_RESULT_REQUIRED,
} from './notification-command.parsers';

/** Delivers a message built from `overrides` on the envelope. */
const parse = (overrides: Record<string, unknown> = {}) =>
  parseContractAnchorRequested(anchorBody(overrides));

/** Delivers a message whose payload is `anchorPayload(overrides)`. */
const parsePayload = (overrides: Record<string, unknown>) =>
  parse({ payload: anchorPayload(overrides) });

const message = (fn: () => unknown): string => {
  try {
    fn();
  } catch (err) {
    expect(err).toBeInstanceOf(NonRetryableError);
    return (err as Error).message;
  }
  throw new Error('expected the parser to throw');
};

describe('parseContractAnchorRequested', () => {
  it('reads the envelope eventId and every payload field', () => {
    expect(parse()).toEqual(anchorCommand());
  });

  it('takes the dedup event from the envelope, not from the payload', () => {
    const eventId = '7c6b5a49-3827-4615-8403-2f1e0d9c8b7a';

    expect(parse({ eventId }).eventId).toBe(eventId);
  });

  it('keeps a null userId null: the recipient has no account', () => {
    const recipients = [
      { userId: null, email: 'contacts@software-buyer.test', role: 'ADMIN' },
    ];

    expect(parsePayload({ recipients }).recipients).toEqual(recipients);
  });

  describe('rejects, without retrying, a message that', () => {
    it('is not JSON', () => {
      expect(() =>
        parseContractAnchorRequested(Buffer.from('{not json')),
      ).toThrow(NonRetryableError);
    });

    it('has no envelope (a flat Phase 1 style payload)', () => {
      expect(() =>
        parseContractAnchorRequested(
          Buffer.from(JSON.stringify(anchorPayload())),
        ),
      ).toThrow(NonRetryableError);
    });

    it.each([
      ['eventId', { eventId: 'not-a-uuid' }],
      ['eventVersion', { eventVersion: 0 }],
      ['occurredAt', { occurredAt: '2026-11-08 09:30:00' }],
      ['producer', { producer: 'audit-service' }],
      ['aggregateId', { aggregateId: '' }],
      ['correlationId', { correlationId: null }],
      ['eventType', { eventType: 'notification.audit_digest_requested' }],
      ['payload', { payload: [] }],
    ])('has a bad %s', (_field, overrides) => {
      expect(() => parse(overrides)).toThrow(NonRetryableError);
    });

    /*
     * The command envelope of notification-commands.yaml is stricter than
     * event-envelope.yaml: correlationId is required and `subject` is not an
     * allowed property.
     */
    it('carries a `subject`, which the command envelope does not allow', () => {
      expect(() => parse({ subject: { type: 'CONTRACT', id: 'c-1' } })).toThrow(
        NonRetryableError,
      );
    });

    it.each([
      ['contractId', { contractId: 'contract-1' }],
      ['contractTermsSnapshot', { contractTermsSnapshot: 'frozen' }],
      ['signedContentHash', { signedContentHash: 'A'.repeat(64) }],
      ['signedContentHash length', { signedContentHash: 'ab' }],
      ['signedAt', { signedAt: 'yesterday' }],
    ])('has a bad %s', (_field, overrides) => {
      expect(() => parsePayload(overrides)).toThrow(NonRetryableError);
    });

    it('adds a payload property the command does not declare', () => {
      expect(() => parsePayload({ otsProof: 'base64' })).toThrow(
        NonRetryableError,
      );
    });

    it.each([
      ['no recipients at all', []],
      ['a recipient that is not an object', ['buyer@anchor.test']],
      ['a recipient without userId', [{ email: 'b@x.test', role: 'BUYER' }]],
      [
        'a userId that is not a UUID',
        [{ userId: 'u-1', email: 'b@x.test', role: 'BUYER' }],
      ],
      [
        'an address that is not one',
        [{ userId: null, email: 'not an address', role: 'BUYER' }],
      ],
      [
        'a role outside the contract',
        [{ userId: null, email: 'b@x.test', role: 'BANK' }],
      ],
      [
        'a property the recipient does not declare',
        [{ userId: null, email: 'b@x.test', role: 'BUYER', name: 'Bao' }],
      ],
    ])('has %s', (_case, recipients) => {
      expect(() => parsePayload({ recipients })).toThrow(NonRetryableError);
    });
  });

  /*
   * These messages are logged on every dead-letter (review #172), and
   * `payload.recipients` holds email addresses.
   */
  describe('error messages', () => {
    it('name the command, the field and the event, and nothing else', () => {
      const text = message(() => parsePayload({ signedAt: 'yesterday' }));

      expect(text).toContain('notification.contract_anchor_requested');
      expect(text).toContain('"signedAt"');
      expect(text).toContain(anchorEnvelope().eventId as string);
      expect(text).not.toContain('yesterday');
    });

    it('never quote a recipient address, not even the bad one', () => {
      const text = message(() =>
        parsePayload({
          recipients: [
            { userId: null, email: 'leaked@anchor.test', role: 'BANK' },
          ],
        }),
      );

      expect(text).toContain('recipients[0].role');
      expect(text).not.toContain('leaked@anchor.test');
      expect(text).not.toContain('BANK');
    });

    it('point at the recipient by index', () => {
      const text = message(() =>
        parsePayload({
          recipients: [
            { userId: null, email: 'ok@anchor.test', role: 'BUYER' },
            { userId: null, email: 'bad address', role: 'SELLER' },
          ],
        }),
      );

      expect(text).toContain('recipients[1].email');
      expect(text).not.toContain('bad address');
    });

    it('omit an event id that is not a plain id', () => {
      const text = message(() => parse({ eventId: 'not-a-uuid-but-<script>' }));

      expect(text).not.toContain('<script>');
    });
  });
});

describe('parseActivationCancelRequested', () => {
  const parseCancel = (payload: Record<string, unknown> = {}) =>
    parseActivationCancelRequested(
      cancelBody({ payload: cancelPayload(payload) }),
    );

  it('reads the envelope eventId and every payload field', () => {
    expect(parseCancel()).toEqual(cancelCommand());
  });

  it('reads a seller request', () => {
    expect(parseCancel({ requestedByRole: 'SELLER' }).requestedByRole).toBe(
      'SELLER',
    );
  });

  it.each([
    ['requestedByRole', 'OPERATOR', 'must be one of BUYER, SELLER'],
    ['requestedByRole', null, 'must be one of BUYER, SELLER'],
    ['oneSidedCancelAllowedFrom', '2026-11-14', 'RFC 3339'],
    ['contractId', 'contract-1', 'must be a UUID'],
    ['trigger', 'ONE_SIDED', 'unknown payload property "trigger"'],
  ])(
    'rejects %s = %p, naming the field and not the value',
    (field, value, reason) => {
      const text = message(() => parseCancel({ [field]: value }));

      expect(text).toContain(
        'Malformed notification.activation_cancel_requested',
      );
      expect(text).toContain(reason);
      if (typeof value === 'string' && field !== 'trigger') {
        expect(text).not.toContain(`"${value}"`);
      }
    },
  );

  it('rejects a missing requestedByRole', () => {
    const payload = cancelPayload();
    delete payload.requestedByRole;

    expect(
      message(() =>
        parseActivationCancelRequested(
          Buffer.from(JSON.stringify(cancelEnvelope({ payload }))),
        ),
      ),
    ).toContain('requestedByRole');
  });

  it('rejects the anchor command, whose eventType is not this queue’s', () => {
    expect(
      message(() => parseActivationCancelRequested(anchorBody())),
    ).toContain("is not this queue's command");
  });
});

describe('parseContractActivationFailedRequested', () => {
  const parseFailed = (payload: Record<string, unknown> = {}) =>
    parseContractActivationFailedRequested(
      activationFailedBody({ payload: activationFailedPayload(payload) }),
    );

  it('reads the envelope eventId and every payload field', () => {
    expect(parseFailed()).toEqual(activationFailedCommand());
  });

  it.each([
    ['an empty buyer state', { buyerDepositState: '' }],
    ['a state it has no words for', { sellerDepositState: 'RETURNED' }],
    ['no next action', { nextActions: [] }],
    [
      'a repeated next action',
      { nextActions: ['RETRY_DEPOSIT_LOCK', 'RETRY_DEPOSIT_LOCK'] },
    ],
    ['failedLeg BOTH', { failedLeg: 'BOTH' }],
  ])('accepts %s, as the contract does', (_case, patch) => {
    expect(parseFailed(patch)).toMatchObject(patch);
  });

  it.each([
    [
      'failedLeg',
      'NONE',
      'field "failedLeg" must be one of BUYER, SELLER, BOTH',
    ],
    ['buyerDepositState', null, 'field "buyerDepositState" must be a string'],
    ['sellerDepositState', 7, 'field "sellerDepositState" must be a string'],
    [
      'nextActions',
      'RETRY_DEPOSIT_LOCK',
      'field "nextActions" must be an array',
    ],
    [
      'nextActions',
      ['RETRY_DEPOSIT_LOCK', 'MARK_FAILED'],
      'nextActions[1] must be one of RETRY_DEPOSIT_LOCK, REQUEST_ACTIVATION_CANCEL',
    ],
    ['oneSidedCancelAllowedFrom', '2026-11-14', 'RFC 3339'],
    ['contractId', 'contract-1', 'must be a UUID'],
    [
      'reason',
      'BUYER: insufficient funds',
      'unknown payload property "reason"',
    ],
  ])(
    'rejects %s = %p, naming the field and not the value',
    (field, value, reason) => {
      const text = message(() => parseFailed({ [field]: value }));

      expect(text).toContain(
        'Malformed notification.contract_activation_failed_requested',
      );
      expect(text).toContain(reason);
      if (typeof value === 'string' && field !== 'reason') {
        expect(text).not.toContain(`"${value}"`);
      }
      if (field === 'reason') expect(text).not.toContain('insufficient');
      if (Array.isArray(value)) expect(text).not.toContain('MARK_FAILED');
    },
  );

  it.each(CONTRACT_ACTIVATION_FAILED_FIELDS)(
    'rejects a missing %s',
    (field) => {
      const payload = activationFailedPayload();
      delete payload[field];

      expect(
        message(() =>
          parseContractActivationFailedRequested(
            activationFailedBody({ payload }),
          ),
        ),
      ).toContain(field);
    },
  );

  it('rejects the activation-cancel command, whose eventType is not this queue’s', () => {
    expect(
      message(() => parseContractActivationFailedRequested(cancelBody())),
    ).toContain("is not this queue's command");
  });
});

describe('parseContractTerminatedRequested', () => {
  const parseTerminated = (payload: Record<string, unknown> = {}) =>
    parseContractTerminatedRequested(
      terminatedBody({ payload: terminatedPayload(payload) }),
    );

  it('reads the envelope eventId and every payload field of a withdrawn offer', () => {
    expect(parseTerminated()).toEqual(terminatedCommand());
  });

  it('reads every field of a breach termination too: all types parse', () => {
    expect(parseTerminated(BREACH_TERMINATION)).toMatchObject(
      BREACH_TERMINATION,
    );
  });

  it.each([
    ['terminationType', 'CANCELLED', 'must be one of WITHDRAW_OFFER,'],
    ['requestedBy', 'OPERATOR', 'must be one of BUYER, SELLER, SYSTEM'],
    ['finalBreachingRole', 'SYSTEM', 'must be one of BUYER, SELLER'],
    ['breachReasonCode', 'LATE', 'field "breachReasonCode" must be one of'],
    [
      'remedyDecisionId',
      'decision-1',
      'field "remedyDecisionId" must be a UUID',
    ],
    [
      'replacesContractId',
      7,
      'field "replacesContractId" must be a non-empty string',
    ],
    [
      'affectedMilestoneIds',
      null,
      'field "affectedMilestoneIds" must be an array',
    ],
    ['affectedMilestoneIds', ['m-1'], 'affectedMilestoneIds[0] must be a UUID'],
    [
      'affectedMilestoneIds',
      [
        '2c3d4e5f-6a7b-4c8d-8e9f-1a2b3c4d5e6f',
        '2c3d4e5f-6a7b-4c8d-8e9f-1a2b3c4d5e6f',
      ],
      'affectedMilestoneIds[1] repeats an earlier item',
    ],
    ['remedyLegs', {}, 'field "remedyLegs" must be an array'],
    ['reason', 'changed my mind', 'unknown payload property "reason"'],
  ])(
    'rejects %s = %p, naming the field and not the value',
    (field, value, reason) => {
      const text = message(() => parseTerminated({ [field]: value }));

      expect(text).toContain(
        'Malformed notification.contract_terminated_requested',
      );
      expect(text).toContain(reason);
      if (typeof value === 'string' && field !== 'reason') {
        expect(text).not.toContain(`"${value}"`);
      }
      expect(text).not.toContain('changed my mind');
    },
  );

  it.each(CONTRACT_TERMINATED_FIELDS)(
    'rejects a missing %s, nullable or not',
    (field) => {
      const payload = terminatedPayload();
      delete payload[field];

      expect(
        message(() =>
          parseContractTerminatedRequested(terminatedBody({ payload })),
        ),
      ).toContain(field);
    },
  );

  describe('remedyLegs', () => {
    const leg = (BREACH_TERMINATION.remedyLegs as Record<string, unknown>[])[0];
    const withLeg = (patch: Record<string, unknown>) =>
      parseTerminated({
        ...BREACH_TERMINATION,
        remedyLegs: [{ ...leg, ...patch }],
      });

    it.each([
      ['remedyLegId', 'leg-1', 'remedyLegs[0].remedyLegId must be a UUID'],
      ['remedyType', 'FINE', 'remedyLegs[0].remedyType must be one of'],
      ['fundType', 'ESCROW', 'remedyLegs[0].fundType must be one of'],
      ['role', 'SYSTEM', 'remedyLegs[0].role must be one of BUYER, SELLER'],
      ['amount', '7126425', 'remedyLegs[0].amount must be a decimal string'],
      ['amount', 7126425, 'remedyLegs[0].amount must be a decimal string'],
      ['amount', '-1.00', 'remedyLegs[0].amount must be a decimal string'],
      ['amount', `${'9'.repeat(17)}.00`, 'at most 19 characters'],
      ['note', 'x', 'unknown property "note" in remedyLegs[0]'],
    ])('rejects %s = %p', (field, value, reason) => {
      expect(message(() => withLeg({ [field]: value }))).toContain(reason);
    });

    it('accepts the largest amount the contract allows', () => {
      const amount = `${'9'.repeat(16)}.99`;
      expect(withLeg({ amount }).remedyLegs[0].amount).toBe(amount);
    });
  });

  it('rejects the activation-cancel command, whose eventType is not this queue’s', () => {
    expect(
      message(() => parseContractTerminatedRequested(cancelBody())),
    ).toContain("is not this queue's command");
  });
});

describe('parseUserKycResultRequested', () => {
  const parseKyc = (payload: Record<string, unknown> = kycPayload()) =>
    parseUserKycResultRequested(kycBody({ payload }));

  it('reads the envelope eventId and every payload field of a rejection', () => {
    expect(parseKyc()).toEqual(kycCommand());
  });

  it('reads a verification, whose reason user-service leaves out, as a null reason', () => {
    expect(parseKyc(kycVerifiedPayload())).toEqual({
      ...kycCommand(),
      result: 'VERIFIED',
      reason: null,
    });
  });

  it.each([
    ['a rejection without a reason', kycPayload({ reason: undefined })],
    ['a verification with a reason', kycVerifiedPayload({ reason: 'ok' })],
    ['an empty organisation name', kycPayload({ organizationName: '' })],
    ['an empty reason', kycPayload({ reason: '' })],
  ])('accepts %s, as the contract does', (_case, payload) => {
    // JSON drops undefined, as it drops the property.
    const sent = JSON.parse(JSON.stringify(payload)) as Record<string, unknown>;
    expect(() => parseKyc(sent)).not.toThrow();
  });

  it.each([
    ['result', 'PENDING', 'field "result" must be one of VERIFIED, REJECTED'],
    ['result', 'rejected', 'field "result" must be one of VERIFIED, REJECTED'],
    ['reason', null, 'field "reason" must be a string, got null'],
    ['organizationName', null, 'field "organizationName" must be a string'],
    ['recipientEmail', 'owner.kyc.test', 'must be an email address'],
    ['userId', 'user-1', 'field "userId" must be a UUID'],
    ['decidedAt', '2026-11-11', 'RFC 3339'],
    ['recipients', [], 'unknown payload property "recipients"'],
  ])(
    'rejects %s = %p, naming the field and not the value',
    (field, value, reason) => {
      const text = message(() => parseKyc(kycPayload({ [field]: value })));

      expect(text).toContain(
        'Malformed notification.user_kyc_result_requested',
      );
      expect(text).toContain(reason);
      if (typeof value === 'string') expect(text).not.toContain(`"${value}"`);
    },
  );

  it.each(USER_KYC_RESULT_REQUIRED)('rejects a missing %s', (field) => {
    const payload = kycPayload();
    delete payload[field];

    expect(message(() => parseKyc(payload))).toContain(`"${field}"`);
  });

  it('never quotes the address it rejects', () => {
    const text = message(() =>
      parseKyc(kycPayload({ recipientEmail: `${KYC_EMAIL} ` })),
    );

    expect(text).not.toContain(KYC_EMAIL);
  });

  it('rejects the command from another producer', () => {
    expect(
      message(() =>
        parseUserKycResultRequested(kycBody({ producer: 'contract-service' })),
      ),
    ).toContain('producer must be "user-service"');
  });

  it('rejects the activation-cancel command, whose eventType is not this queue’s', () => {
    expect(message(() => parseUserKycResultRequested(cancelBody()))).toContain(
      "is not this queue's command",
    );
  });
});
