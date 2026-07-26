/**
 * GET /api/health — unauthenticated liveness probe.
 *
 * Deliberately touches no secrets, no environment validation and no Blob
 * API: it only proves the function is deployed and executing, so uptime
 * monitors and deploy pipelines can gate alias promotion on it.
 */

export function GET(): Response {
  return new Response(JSON.stringify({ ok: true, data: { status: 'UP' } }), {
    status: 200,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': 'no-store'
    }
  });
}
