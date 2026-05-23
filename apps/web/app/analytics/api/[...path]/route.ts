import { NextRequest, NextResponse } from "next/server";

export const dynamic = "force-dynamic";

type RouteContext = {
  params: Promise<{
    path?: string[];
  }>;
};

const INTERNAL_ANALYTICS_BASE =
  process.env.ANALYTICS_API_INTERNAL_BASE ?? "http://localhost:8081";

const FORWARDED_REQUEST_HEADERS = [
  "authorization",
  "traceparent",
  "tracestate",
  "x-request-id"
];

const FORWARDED_RESPONSE_HEADERS = [
  "cache-control",
  "content-type",
  "vary",
  "www-authenticate",
  "x-request-id",
  "x-trace-id"
];

function analyticsTarget(path: string[], request: NextRequest) {
  const base = INTERNAL_ANALYTICS_BASE.endsWith("/")
    ? INTERNAL_ANALYTICS_BASE
    : `${INTERNAL_ANALYTICS_BASE}/`;
  const target = new URL(path.map(encodeURIComponent).join("/"), base);
  target.search = request.nextUrl.search;
  return target;
}

function forwardedHeaders(request: NextRequest) {
  const headers = new Headers();
  for (const header of FORWARDED_REQUEST_HEADERS) {
    const value = request.headers.get(header);
    if (value) {
      headers.set(header, value);
    }
  }
  return headers;
}

function responseHeaders(response: Response) {
  const headers = new Headers();
  for (const header of FORWARDED_RESPONSE_HEADERS) {
    const value = response.headers.get(header);
    if (value) {
      headers.set(header, value);
    }
  }
  return headers;
}

export async function GET(request: NextRequest, context: RouteContext) {
  const { path = [] } = await context.params;

  try {
    const response = await fetch(analyticsTarget(path, request), {
      cache: "no-store",
      headers: forwardedHeaders(request),
      method: "GET"
    });

    return new NextResponse(response.body, {
      headers: responseHeaders(response),
      status: response.status,
      statusText: response.statusText
    });
  } catch {
    return NextResponse.json(
      {
        error: {
          code: "ANALYTICS_PROXY_FAILED",
          message: "Analytics query service is unavailable",
          details: []
        }
      },
      { status: 502 }
    );
  }
}
