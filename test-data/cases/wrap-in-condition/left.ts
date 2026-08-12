export function flush(queue: Job[]): void {
  queue.forEach((job) => job.run());
  queue.length = 0;
}
