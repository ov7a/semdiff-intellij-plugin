export function flush(queue: Job[]): void {
  if (queue.length > 0) {
    queue.forEach((job) => job.run());
    queue.length = 0;
  }
}
