import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import AuditLogPage from './AuditLogPage.vue'

/** Wait for all pending promises to resolve */
async function flushPromises() {
  await new Promise(resolve => setTimeout(resolve, 0));
}

// Hoist mock functions so they are available when vi.mock is hoisted
const {
  mockListAuditRecords,
  mockGetAuditRecord,
  mockListAuditCategories,
  mockExportAuditRecords,
} = vi.hoisted(() => ({
  mockListAuditRecords: vi.fn(),
  mockGetAuditRecord: vi.fn(),
  mockListAuditCategories: vi.fn(),
  mockExportAuditRecords: vi.fn(),
}));

// Mock the AuditAPI module
mockListAuditRecords.mockResolvedValue({
  items: [
    {
      id: 'aud-1',
      createdAt: '2026-05-26T10:00:00Z',
      category: 'ADMIN_AUDIT',
      action: 'ADMIN_LIST_TENANTS',
      actorType: 'ADMIN',
      actorId: 'admin-1',
      resourceType: 'tenant',
      resourceId: 'tenant-a',
      targetTenantId: 'tenant-a',
      result: 'SUCCESS',
      requestId: 'req-1',
      traceId: 'trace-1',
    },
    {
      id: 'aud-2',
      createdAt: '2026-05-26T09:00:00Z',
      category: 'ADMIN_AUDIT',
      action: 'ADMIN_DELETE_WORKSPACE',
      actorType: 'ADMIN',
      actorId: 'admin-2',
      resourceType: 'workspace',
      resourceId: 'ws-1',
      targetTenantId: 'tenant-b',
      result: 'DENIED',
      requestId: 'req-2',
      traceId: 'trace-2',
    },
  ],
  page: 0,
  size: 50,
  total: 2,
});

mockGetAuditRecord.mockResolvedValue({
  id: 'aud-1',
  createdAt: '2026-05-26T10:00:00Z',
  category: 'ADMIN_AUDIT',
  action: 'ADMIN_LIST_TENANTS',
  actorType: 'ADMIN',
  actorId: 'admin-1',
  resourceType: 'tenant',
  resourceId: 'tenant-a',
  payload: { targetTenantId: 'tenant-a', result: 'SUCCESS', apiKey: 'sk-secret-key' },
});

mockListAuditCategories.mockResolvedValue([
  'ADMIN_AUDIT',
  'DATA_GOVERNANCE',
  'IDENTITY',
  'RENDER',
  'UNKNOWN',
]);

mockExportAuditRecords.mockResolvedValue(new Blob(['test'], { type: 'text/csv' }));

vi.mock('@/api/admin/audit', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/admin/audit')>();
  return {
    ...actual,
    AuditAPI: {
      listAuditRecords: mockListAuditRecords,
      getAuditRecord: mockGetAuditRecord,
      listAuditCategories: mockListAuditCategories,
      exportAuditRecords: mockExportAuditRecords,
    },
  };
});

describe('AuditLogPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders audit log table with records', async () => {
    const wrapper = mount(AuditLogPage);
    await flushPromises();

    const rows = wrapper.findAll('tbody tr');
    expect(rows.length).toBe(2);
  });

  it('displays category badges', async () => {
    const wrapper = mount(AuditLogPage);
    await flushPromises();

    const html = wrapper.html();
    expect(html).toContain('ADMIN_AUDIT');
  });

  it('displays result badges', async () => {
    const wrapper = mount(AuditLogPage);
    await flushPromises();

    const html = wrapper.html();
    expect(html).toContain('SUCCESS');
    expect(html).toContain('DENIED');
  });

  it('calls listAuditRecords on mount', async () => {
    mount(AuditLogPage);
    await flushPromises();

    expect(mockListAuditRecords).toHaveBeenCalled();
  });

  it('calls listAuditCategories on mount', async () => {
    mount(AuditLogPage);
    await flushPromises();

    expect(mockListAuditCategories).toHaveBeenCalled();
  });

  it('shows empty state when no records', async () => {
    mockListAuditRecords.mockResolvedValueOnce({
      items: [],
      page: 0,
      size: 50,
      total: 0,
    });

    const wrapper = mount(AuditLogPage);
    await flushPromises();

    expect(wrapper.findComponent({ name: 'EmptyState' }).exists()).toBe(true);
  });

  it('shows error state on API failure', async () => {
    mockListAuditRecords.mockRejectedValueOnce(new Error('API Error'));

    const wrapper = mount(AuditLogPage);
    await flushPromises();

    expect(wrapper.text()).toContain('API Error');
  });

  it('does not display sensitive fields in table', async () => {
    const wrapper = mount(AuditLogPage);
    await flushPromises();

    const html = wrapper.html();
    expect(html).not.toContain('password');
    expect(html).not.toContain('token');
    expect(html).not.toContain('apiKey');
    expect(html).not.toContain('signedUrl');
  });

  it('payload shows REDACTED for sensitive keys in detail', async () => {
    const wrapper = mount(AuditLogPage);
    await flushPromises();

    const rows = wrapper.findAll('tbody tr');
    await rows[0].trigger('click');
    await flushPromises();

    const html = wrapper.html();
    expect(html).toContain('[REDACTED]');
    expect(html).not.toContain('sk-secret-key');
  });

  it('opens detail drawer on row click', async () => {
    const wrapper = mount(AuditLogPage);
    await flushPromises();

    const rows = wrapper.findAll('tbody tr');
    await rows[0].trigger('click');
    await flushPromises();

    expect(wrapper.find('.fixed.inset-0').exists()).toBe(true);
  });

  it('closes detail drawer on close button', async () => {
    const wrapper = mount(AuditLogPage);
    await flushPromises();

    const rows = wrapper.findAll('tbody tr');
    await rows[0].trigger('click');
    await flushPromises();

    const closeBtn = wrapper.find('.fixed.inset-0 button');
    if (closeBtn.exists()) {
      await closeBtn.trigger('click');
      await flushPromises();
    }

    expect(wrapper.find('.fixed.inset-0').exists()).toBe(false);
  });

  it('has Export CSV button', async () => {
    const wrapper = mount(AuditLogPage);
    await flushPromises();

    const exportBtn = wrapper.findAll('button').find(b => b.text().includes('Export CSV'));
    expect(exportBtn).toBeTruthy();
  });

  it('calls exportAuditRecords on export click', async () => {
    const wrapper = mount(AuditLogPage);
    await flushPromises();

    const exportBtn = wrapper.findAll('button').find(b => b.text().includes('Export CSV'));
    await exportBtn!.trigger('click');
    await flushPromises();

    expect(mockExportAuditRecords).toHaveBeenCalled();
  });

  it('export uses current filter parameters', async () => {
    const wrapper = mount(AuditLogPage);
    await flushPromises();

    const exportBtn = wrapper.findAll('button').find(b => b.text().includes('Export CSV'));
    await exportBtn!.trigger('click');
    await flushPromises();

    expect(mockExportAuditRecords).toHaveBeenCalledWith(
      expect.objectContaining({
        limit: 1000,
      })
    );
  });

  it('shows error on export failure', async () => {
    mockExportAuditRecords.mockRejectedValueOnce(new Error('Export failed'));

    const wrapper = mount(AuditLogPage);
    await flushPromises();

    const exportBtn = wrapper.findAll('button').find(b => b.text().includes('Export CSV'));
    await exportBtn!.trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('Export failed');
  });
});
