import type { Request, Response } from 'express';
import { DIRECT_UPLOAD_POLICY } from '../../application/ingest-policy';
import { declaredLength } from './store-on-behalf-of.request';

/**
 * What to do with the part of an upload nobody read, before answering: the
 * request was refused before its body was touched (401, 400, 413, a repeat),
 * or the store stopped part way (503). The answer must still reach a caller
 * who may be in the middle of sending the body.
 *
 * - A body declared within the cap is read to its end and thrown away, and
 *   the promise resolves only then. Answering first would not do: once the
 *   answer is out, Node closes a `Connection: close` socket, and the bytes
 *   the caller is still sending make the peer reset it, so the caller sees
 *   ECONNRESET instead of the answer. Node's parser holds the body to its
 *   Content-Length, so this reads at most 10 MiB.
 * - Any other body (over the cap, chunked, no length) is not read at all:
 *   the answer says `Connection: close` and goes at once, rather than after
 *   an unbounded body read for nothing. A caller still sending may then see
 *   a reset instead of the answer; it should not send such a body.
 *
 * Safe to call more than once; resolves at once when the body was read.
 */
export function discardUnreadBody(req: Request, res: Response): Promise<void> {
  if (req.complete || req.destroyed) return Promise.resolve();
  const length = declaredLength(req.headers);
  if (length === null || length > DIRECT_UPLOAD_POLICY.maxBytes) {
    if (!res.headersSent) res.setHeader('Connection', 'close');
    return Promise.resolve();
  }
  return new Promise((resolve) => {
    const done = () => resolve();
    req.once('end', done);
    // The caller gave up (or the server timed the request out).
    req.once('close', done);
    req.once('error', done);
    req.unpipe();
    req.resume();
  });
}
