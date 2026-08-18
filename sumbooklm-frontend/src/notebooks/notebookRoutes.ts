/**
 * Addresses the Sumbooks of the signed-in user live under.
 *
 * The path is built in one place because it is produced by the cards of the overview and consumed by
 * the router. A literal repeated in both would let one of them move without the other.
 */
export function sumbookPath(notebookId: string): string {
  return `/dashboard/sumbook/${notebookId}`;
}
