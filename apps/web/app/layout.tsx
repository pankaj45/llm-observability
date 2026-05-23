import "@mantine/core/styles.css";

import type { Metadata } from "next";
import Link from "next/link";
import { ColorSchemeScript, Group, MantineProvider, Text } from "@mantine/core";
import { IconBrandHipchat, IconChartBar } from "@tabler/icons-react";

export const metadata: Metadata = {
  title: "LLM Observability Platform",
  description: "AI inference chatbot and observability platform.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <head>
        <ColorSchemeScript />
      </head>
      <body style={{ margin: 0, fontFamily: "var(--mantine-font-family)" }}>
        <MantineProvider defaultColorScheme="light">
          <header
            style={{
              height: 52,
              borderBottom: "1px solid var(--mantine-color-gray-3)",
              background: "var(--mantine-color-white)",
              display: "flex",
              alignItems: "center",
              padding: "0 20px",
              position: "sticky",
              top: 0,
              zIndex: 100,
            }}
          >
            <Group gap="xs" align="center" style={{ flex: 1 }}>
              <Text fw={700} size="sm" c="dark" style={{ letterSpacing: "-0.02em" }}>
                LLM Observability
              </Text>
            </Group>
            <Group gap={4}>
              <Link
                href="/"
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: 6,
                  padding: "6px 12px",
                  borderRadius: 6,
                  textDecoration: "none",
                  fontSize: 14,
                  fontWeight: 500,
                  color: "var(--mantine-color-dark-6)",
                }}
              >
                <IconBrandHipchat size={16} />
                Chat
              </Link>
              <Link
                href="/analytics"
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: 6,
                  padding: "6px 12px",
                  borderRadius: 6,
                  textDecoration: "none",
                  fontSize: 14,
                  fontWeight: 500,
                  color: "var(--mantine-color-dark-6)",
                }}
              >
                <IconChartBar size={16} />
                Analytics
              </Link>
            </Group>
          </header>
          <main style={{ height: "calc(100vh - 52px)", overflow: "hidden" }}>{children}</main>
        </MantineProvider>
      </body>
    </html>
  );
}
