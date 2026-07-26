import type { NextAuthOptions } from 'next-auth';
import CredentialsProvider from 'next-auth/providers/credentials';
import FacebookProvider from 'next-auth/providers/facebook';
import GoogleProvider from 'next-auth/providers/google';
import { authenticateCredentials } from './auth-service';
import { VercelBlobAdapter } from './blob-adapter';
import { AUTH_SECRET, SESSION_IDLE_TIMEOUT_SECONDS } from './constants';

export const authOptions: NextAuthOptions = {
  adapter: VercelBlobAdapter(),
  secret: AUTH_SECRET,
  providers: [
    CredentialsProvider({
      name: 'Email and password',
      credentials: {
        email: { label: 'Email', type: 'email' },
        password: { label: 'Mật khẩu', type: 'password' }
      },
      async authorize(credentials) {
        if (!credentials?.email || !credentials.password) return null;
        return authenticateCredentials(credentials.email, credentials.password);
      }
    }),
    GoogleProvider({
      clientId: process.env.GOOGLE_CLIENT_ID ?? '',
      clientSecret: process.env.GOOGLE_CLIENT_SECRET ?? ''
    }),
    FacebookProvider({
      clientId: process.env.FACEBOOK_CLIENT_ID ?? '',
      clientSecret: process.env.FACEBOOK_CLIENT_SECRET ?? ''
    })
  ],
  session: {
    strategy: 'jwt',
    maxAge: SESSION_IDLE_TIMEOUT_SECONDS,
    updateAge: 60
  },
  jwt: {
    maxAge: SESSION_IDLE_TIMEOUT_SECONDS
  },
  pages: {
    signIn: '/vi/login'
  },
  useSecureCookies: process.env.NODE_ENV === 'production',
  callbacks: {
    async jwt({ token, user }) {
      if (user) token.userId = user.id;
      return token;
    },
    async session({ session, token }) {
      if (session.user && token.userId) session.user.id = token.userId;
      return session;
    }
  }
};
