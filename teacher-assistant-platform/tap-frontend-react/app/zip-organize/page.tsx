"use client";

import { useMemo, useState } from "react";
import AppLayout from "@/components/layout/AppLayout";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { apiFetch, session } from "@/lib/api";
import { Download, FolderTree, Loader2, RefreshCw, UploadCloud } from "lucide-react";

const BASE = process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";

type JobItem = {
  id: number;
  originalPath: string;
  finalPath?: string;
};

export default function ZipOrganizePage() {
  const [file, setFile] = useState<File | null>(null);
  const [jobId, setJobId] = useState("");
  const [uploading, setUploading] = useState(false);
  const [loading, setLoading] = useState(false);
  const [reportLoading, setReportLoading] = useState(false);
  const [error, setError] = useState("");
  const [job, setJob] = useState<any | null>(null);
  const [report, setReport] = useState("");

  const reviewCount = useMemo(() => {
    const items = (job?.items ?? []) as JobItem[];
    return items.filter(item => (item.finalPath ?? "").startsWith("Review_Required/")).length;
  }, [job]);

  const submit = async () => {
    if (!file) return;
    setUploading(true);
    setError("");
    setReport("");
    try {
      const fd = new FormData();
      fd.append("file", file);
      const token = session.token();
      const res = await fetch(`${BASE}/api/zip-organize/jobs`, {
        method: "POST",
        headers: token ? { Authorization: `Bearer ${token}` } : undefined,
        body: fd,
      });
      const json = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(json?.message || json?.error || `HTTP ${res.status}`);
      }
      const nextJobId = String(json?.data?.jobId ?? "");
      setJobId(nextJobId);
      setJob(json?.data ? { ...json.data, items: [] } : null);
    } catch (e: any) {
      setError(e?.message ?? "Upload failed");
    } finally {
      setUploading(false);
    }
  };

  const refresh = async () => {
    if (!jobId) return;
    setLoading(true);
    setError("");
    try {
      const res = await apiFetch(`/api/zip-organize/jobs/${jobId}`);
      setJob(res?.data ?? null);
    } catch (e: any) {
      setError(e?.message ?? "Refresh failed");
    } finally {
      setLoading(false);
    }
  };

  const loadReport = async () => {
    if (!jobId) return;
    setReportLoading(true);
    setError("");
    try {
      const res = await apiFetch(`/api/zip-organize/jobs/${jobId}/report`);
      setReport(JSON.stringify(res?.data ?? {}, null, 2));
    } catch (e: any) {
      setError(e?.message ?? "Report load failed");
    } finally {
      setReportLoading(false);
    }
  };

  const download = async () => {
    if (!jobId) return;
    const token = session.token();
    const res = await fetch(`${BASE}/api/zip-organize/jobs/${jobId}/download`, {
      headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    });
    if (!res.ok) {
      setError(`Download failed: HTTP ${res.status}`);
      return;
    }
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${job?.originalFilename?.replace(/\.zip$/i, "") ?? "organized"}-organized.zip`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <AppLayout>
      <div className="mx-auto flex max-w-6xl flex-col gap-6 px-6 py-8">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">ZIP Smart Organize</h1>
            <p className="mt-1 text-sm text-muted-foreground">
              Upload a ZIP of papers or teaching files. The backend classifies content, renames files,
              builds a cleaner folder tree, and returns a new ZIP.
            </p>
          </div>
          <div className="flex items-center gap-2 rounded-xl border border-border bg-card px-4 py-3">
            <FolderTree className="h-4 w-4 text-primary" />
            <div className="text-sm">
              <div className="font-medium">Isolated pipeline</div>
              <div className="text-xs text-muted-foreground">No writes into Document Center tables</div>
            </div>
          </div>
        </div>

        <div className="grid gap-6 lg:grid-cols-[1.15fr_0.85fr]">
          <section className="space-y-4 rounded-2xl border border-border bg-card p-6">
            <div>
              <h2 className="font-medium">Create Job</h2>
              <p className="mt-1 text-xs text-muted-foreground">
                Supported now: `pdf`, `doc`, `docx`, `txt`. Low-confidence files go to `Review_Required/`.
              </p>
            </div>

            <div className="space-y-4 rounded-xl border border-dashed border-border bg-muted/30 p-5">
              <div className="flex items-center gap-3">
                <UploadCloud className="h-5 w-5 text-primary" />
                <div className="text-sm">
                  <div className="font-medium">{file ? file.name : "Choose a ZIP file"}</div>
                  <div className="text-xs text-muted-foreground">
                    {file ? `${(file.size / 1024 / 1024).toFixed(2)} MB` : "Suggested max size: 50 MB"}
                  </div>
                </div>
              </div>
              <Input type="file" accept=".zip,application/zip" onChange={e => setFile(e.target.files?.[0] ?? null)} />
              <Button onClick={submit} disabled={!file || uploading}>
                {uploading ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <UploadCloud className="mr-2 h-4 w-4" />}
                Start ZIP Organize
              </Button>
            </div>

            <div className="grid gap-3 sm:grid-cols-[180px_auto] sm:items-end">
              <div className="space-y-1.5">
                <Label>Job ID</Label>
                <Input value={jobId} onChange={e => setJobId(e.target.value)} placeholder="Filled after upload" />
              </div>
              <div className="flex flex-wrap gap-3">
                <Button variant="outline" onClick={refresh} disabled={!jobId || loading}>
                  {loading ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <RefreshCw className="mr-2 h-4 w-4" />}
                  Refresh
                </Button>
                <Button variant="outline" onClick={loadReport} disabled={!jobId || reportLoading}>
                  {reportLoading ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <FolderTree className="mr-2 h-4 w-4" />}
                  Load Report
                </Button>
                <Button onClick={download} disabled={!job?.downloadReady}>
                  <Download className="mr-2 h-4 w-4" />
                  Download ZIP
                </Button>
              </div>
            </div>

            {error && <p className="text-sm text-destructive">{error}</p>}
          </section>

          <section className="space-y-4 rounded-2xl border border-border bg-card p-6">
            <div>
              <h2 className="font-medium">Overview</h2>
              <p className="mt-1 text-xs text-muted-foreground">Use this panel to track progress and manual review volume.</p>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <Metric label="Status" value={job?.status ?? "-"} />
              <Metric label="Progress" value={job ? `${job.progress ?? 0}%` : "-"} />
              <Metric label="Files" value={job?.totalItems ?? 0} />
              <Metric label="Review" value={reviewCount} />
            </div>
            <Textarea
              readOnly
              value={job ? JSON.stringify(job, null, 2) : ""}
              placeholder="Job details will show up here"
              className="min-h-72 bg-muted/30 font-mono text-xs"
            />
          </section>
        </div>

        <section className="space-y-4 rounded-2xl border border-border bg-card p-6">
          <div>
            <h2 className="font-medium">Report</h2>
            <p className="mt-1 text-xs text-muted-foreground">
              The report includes old/new paths, inferred categories, and files that still need human review.
            </p>
          </div>
          <Textarea
            readOnly
            value={report}
            placeholder="Click Load Report after the job finishes"
            className="min-h-96 bg-muted/30 font-mono text-xs"
          />
        </section>
      </div>
    </AppLayout>
  );
}

function Metric({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="rounded-xl border border-border bg-muted/30 px-4 py-3">
      <div className="text-xs text-muted-foreground">{label}</div>
      <div className="mt-1 text-lg font-semibold tracking-tight">{value}</div>
    </div>
  );
}
