interface VerificationEmailInput {
  email: string;
  name: string;
  verificationUrl: string;
}

export async function sendVerificationEmail(input: VerificationEmailInput): Promise<void> {
  const apiKey = process.env.RESEND_API_KEY;
  const from = process.env.AUTH_EMAIL_FROM;

  if (!apiKey || !from) {
    if (process.env.NODE_ENV === 'production') {
      throw new Error('RESEND_API_KEY and AUTH_EMAIL_FROM must be configured');
    }

    console.info(`[auth] Verification URL for ${input.email}: ${input.verificationUrl}`);
    return;
  }

  const response = await fetch('https://api.resend.com/emails', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${apiKey}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      from,
      to: [input.email],
      subject: 'Xác nhận tài khoản Quản lý gia phả',
      html: `<p>Xin chào ${escapeHtml(input.name)},</p><p>Vui lòng xác nhận tài khoản bằng liên kết bên dưới:</p><p><a href="${escapeHtml(input.verificationUrl)}">Xác nhận email</a></p><p>Liên kết có hiệu lực trong 24 giờ.</p>`
    }),
    signal: AbortSignal.timeout(4_500)
  });

  if (!response.ok) {
    throw new Error(`Email provider returned status ${response.status}`);
  }
}

function escapeHtml(value: string): string {
  return value.replace(/[&<>'"]/g, (character) => {
    const entities: Record<string, string> = {
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      "'": '&#39;',
      '"': '&quot;'
    };

    return entities[character];
  });
}

