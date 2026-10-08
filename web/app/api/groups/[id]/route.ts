import { groupContract, jsonResponse } from "../../../../lib/api-contracts";
import { localCatalog } from "../../../../lib/local-catalog";

export async function GET(
  _request: Request,
  context: { params: Promise<{ id: string }> },
): Promise<Response> {
  const { id } = await context.params;
  return jsonResponse(groupContract(localCatalog, id));
}
