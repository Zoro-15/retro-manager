# ci-logs — durable CI run archive

Every completed `Tests` / `Android CI` run is archived here by
`.github/workflows/ci-logs.yml` (see issue #9).

Layout (unique per run, newest last):

```text
ci-logs/<workflow-slug>/<YYYY-MM-DD>/run-<number>-id-<id>-attempt-<n>-<conclusion>/
  metadata.json   # workflow, conclusion, head SHA/branch, actor, URLs
  logs.zip        # raw run logs
  logs/           # unzipped logs for browser reading
  artifacts/      # junit-xml, HTML reports, APKs (when produced)
```

This branch is write-only for CI; pushes here trigger no workflows.
