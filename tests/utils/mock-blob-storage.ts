export interface MockBlobRecord {
  pathname: string;
  url: string;
  body: string;
  contentType: string;
  uploadedAt: Date;
}

class MockBlobStorage {
  private readonly records = new Map<string, MockBlobRecord>();

  list(prefix?: string): MockBlobRecord[] {
    return Array.from(this.records.values()).filter((blob) => !prefix || blob.pathname.startsWith(prefix));
  }

  put(pathname: string, body: string, contentType = 'application/json; charset=utf-8') {
    const record: MockBlobRecord = {
      pathname,
      url: this.createUrl(pathname),
      body,
      contentType,
      uploadedAt: new Date()
    };

    this.records.set(pathname, record);

    return {
      url: record.url,
      downloadUrl: record.url,
      pathname: record.pathname,
      contentType: record.contentType,
      contentDisposition: `attachment; filename="${pathname.split('/').pop() ?? 'blob'}"`
    };
  }

  get(pathname: string): MockBlobRecord | undefined {
    return this.records.get(pathname);
  }

  getByUrl(url: string): MockBlobRecord | undefined {
    return Array.from(this.records.values()).find((record) => record.url === url);
  }

  head(pathname: string): MockBlobRecord {
    const record = this.get(pathname);

    if (!record) {
      const error = new Error(`Blob not found: ${pathname}`);
      Object.assign(error, { status: 404 });
      throw error;
    }

    return record;
  }

  delete(pathname: string): void {
    this.records.delete(pathname);
  }

  clear(): void {
    this.records.clear();
  }

  private createUrl(pathname: string): string {
    return `https://blob.test/${encodeURIComponent(pathname)}`;
  }
}

export const mockBlobStorage = new MockBlobStorage();
