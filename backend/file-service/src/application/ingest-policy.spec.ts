import { DIRECT_UPLOAD_POLICY, normalizeContentType } from './ingest-policy';

describe('DIRECT_UPLOAD_POLICY (file-service-phase2-design.md §3)', () => {
  it('accepts JPG, PNG and PDF only, up to 10 MiB', () => {
    expect(DIRECT_UPLOAD_POLICY).toEqual({
      channel: 'DIRECT_UPLOAD',
      maxBytes: 10 * 1024 * 1024,
      contentTypes: ['image/jpeg', 'image/png', 'application/pdf'],
    });
  });
});

describe('normalizeContentType', () => {
  it.each([
    ['image/jpeg', 'image/jpeg'],
    ['IMAGE/PNG', 'image/png'],
    ['application/pdf; charset=binary', 'application/pdf'],
    ['  text/csv ;header=present', 'text/csv'],
    [
      'application/vnd.google-earth.kml+xml',
      'application/vnd.google-earth.kml+xml',
    ],
  ])('%p -> %p', (raw, expected) => {
    expect(normalizeContentType(raw)).toBe(expected);
  });

  it.each(['', 'pdf', 'image/', '/png', 'image/png/extra', 'image png'])(
    'rejects %p',
    (raw) => {
      expect(normalizeContentType(raw)).toBeNull();
    },
  );
});
