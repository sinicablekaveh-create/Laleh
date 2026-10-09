import { localCatalog } from "../../../lib/local-catalog";
import { jsonResponse, searchContract } from "../../../lib/api-contracts";
import { analyzeQuery } from "../../../lib/query";

export async function GET(request: Request) {
  const params = new URL(request.url).searchParams;
  const result = searchContract(localCatalog, params);
  if ("error" in result.body) return jsonResponse(result);
  const data = result.body.data;
  const discovery = localCatalog.discover(data.query, data.offset, data.limit, data.filters);
  return jsonResponse({ status: 200, body: { ...result.body, data: {
    ...data, engineVersion: 3, searchVersion: 6, analysis: analyzeQuery(data.query),
    suggestions: discovery.suggestions,
  } } });
}
