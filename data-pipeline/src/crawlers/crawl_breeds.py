"""Controlled raw crawler for the approved PawConnect Wikipedia breed demo."""

import argparse
import csv
import hashlib
import json
import re
import sys
import time
from datetime import datetime, timezone
from html import unescape
from html.parser import HTMLParser
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urljoin, urlparse
from urllib.request import HTTPRedirectHandler, Request, build_opener


USER_AGENT = "PawConnect-BreedCatalog/0.1"
MIN_REQUEST_INTERVAL_SECONDS = 2.0
CONNECT_TIMEOUT_SECONDS = 10
READ_TIMEOUT_SECONDS = 20
MAX_RESPONSE_BYTES = 2 * 1024 * 1024
MAX_RETRIES = 2
RETRIABLE_STATUS_CODES = {408, 429, 500, 502, 503, 504}
CURATED_BREEDS_HEADER = [
    "seed_key",
    "breed_code",
    "breed_name",
    "default_size",
    "min_weight_kg",
    "max_weight_kg",
    "min_age_months",
    "max_age_months",
    "description",
]


class CrawlError(Exception):
    """Base error for a blocked controlled crawl."""


class BatchStopError(CrawlError):
    """Error that must stop the current batch immediately."""


class AllowlistError(CrawlError):
    """Raised when a URL is not explicitly approved."""


class NoRedirectHandler(HTTPRedirectHandler):
    """Expose redirects so their target can be validated before any follow-up request."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class BreedPageParser(HTMLParser):
    """Small tolerant parser for the lead paragraph and infobox rows only."""

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.title_parts = []
        self.paragraphs = []
        self.rows = []
        self._title_depth = 0
        self._paragraph_depth = 0
        self._paragraph_parts = []
        self._row_depth = 0
        self._cell_kind = None
        self._header_parts = []
        self._value_parts = []
        self._ignored_depth = 0

    def handle_starttag(self, tag, attrs):
        if tag in {"script", "style"}:
            self._ignored_depth += 1
            return
        if self._ignored_depth:
            return
        attr_map = dict(attrs)
        if tag == "h1" and attr_map.get("id") == "firstHeading":
            self._title_depth = 1
        elif self._title_depth:
            self._title_depth += 1

        if tag == "p" and not self._paragraph_depth:
            self._paragraph_depth = 1
            self._paragraph_parts = []
        elif self._paragraph_depth:
            self._paragraph_depth += 1

        if tag == "tr":
            self._row_depth = 1
            self._header_parts = []
            self._value_parts = []
        elif self._row_depth:
            self._row_depth += 1

        if self._row_depth and tag in {"th", "td"}:
            self._cell_kind = tag

    def handle_endtag(self, tag):
        if tag in {"script", "style"} and self._ignored_depth:
            self._ignored_depth -= 1
            return
        if self._ignored_depth:
            return
        if self._title_depth:
            self._title_depth -= 1
        if self._paragraph_depth:
            self._paragraph_depth -= 1
            if not self._paragraph_depth:
                text = normalize_text(" ".join(self._paragraph_parts))
                if text:
                    self.paragraphs.append(text)
        if self._row_depth:
            if tag in {"th", "td"}:
                self._cell_kind = None
            self._row_depth -= 1
            if not self._row_depth:
                header = normalize_text(" ".join(self._header_parts))
                value = normalize_text(" ".join(self._value_parts))
                if header and value:
                    self.rows.append((header, value))

    def handle_data(self, data):
        if self._ignored_depth:
            return
        if self._title_depth:
            self.title_parts.append(data)
        if self._paragraph_depth:
            self._paragraph_parts.append(data)
        if self._cell_kind == "th":
            self._header_parts.append(data)
        elif self._cell_kind == "td":
            self._value_parts.append(data)


def project_root():
    return Path(__file__).resolve().parents[3]


def utc_now():
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def normalize_text(value):
    return re.sub(r"\s+", " ", unescape(value or "")).strip()


def sha256_bytes(value):
    return hashlib.sha256(value).hexdigest()


def sha256_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_json(path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise CrawlError(f"Missing source configuration: {path}") from exc
    except json.JSONDecodeError as exc:
        raise CrawlError(f"Invalid JSON source configuration: {exc}") from exc


def load_curated_breed_codes(root):
    path = root / "data-pipeline" / "data" / "curated" / "catalog" / "breeds.csv"
    if not path.exists():
        raise CrawlError(f"Missing curated breed catalog: {path}")
    with path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        if reader.fieldnames != CURATED_BREEDS_HEADER:
            raise CrawlError("Curated breeds.csv header does not match the approved contract")
        return {row["breed_code"]: row["breed_name"] for row in reader}


def validate_config(config, curated_breeds):
    required = {"source_name", "source_base_url", "license_attribution_note", "robots_policy", "allowlist"}
    missing = sorted(required - set(config))
    if missing:
        raise CrawlError(f"sources.json missing fields: {', '.join(missing)}")
    if config["source_name"] != "Wikipedia English":
        raise CrawlError("Only Wikipedia English is approved for this crawler")
    policy = config["robots_policy"]
    if policy.get("status") != "approved":
        raise BatchStopError("Robots policy is not approved; batch stopped")
    allowlist = config["allowlist"]
    if len(allowlist) != 3:
        raise CrawlError("The controlled demo allowlist must contain exactly 3 articles")
    urls = set()
    for item in allowlist:
        if set(item) != {"breed_code", "breed_name", "url"}:
            raise CrawlError("Each allowlist record must contain only breed_code, breed_name and url")
        if item["url"] in urls:
            raise CrawlError(f"Duplicate allowlist URL: {item['url']}")
        urls.add(item["url"])
        ensure_allowlisted(item["url"], config)
        if curated_breeds.get(item["breed_code"]) != item["breed_name"]:
            raise CrawlError(f"Allowlist canonical breed does not match curated catalog: {item['breed_code']}")


def ensure_allowlisted(url, config):
    parsed = urlparse(url)
    if parsed.scheme != "https" or parsed.netloc != "en.wikipedia.org":
        raise AllowlistError(f"URL is outside the approved Wikipedia domain: {url}")
    if not parsed.path.startswith("/wiki/") or parsed.path.startswith("/wiki/Special:"):
        raise AllowlistError(f"URL is outside the approved article path: {url}")
    allowed_urls = {item["url"] for item in config["allowlist"]}
    if url not in allowed_urls:
        raise AllowlistError(f"URL is not in the explicit allowlist: {url}")


def validate_redirect(source_url, redirect_url, config):
    resolved = urljoin(source_url, redirect_url)
    source_domain = urlparse(source_url).netloc
    target_domain = urlparse(resolved).netloc
    if target_domain != source_domain:
        raise AllowlistError(f"Redirect outside the approved domain: {resolved}")
    ensure_allowlisted(resolved, config)
    return resolved


def has_forbidden_content(text):
    if re.search(r"[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}", text, re.IGNORECASE):
        return True
    if re.search(r"(?:\+?\d[\s.-]?){9,}", text):
        return True
    return False


def extract_weight_kg(rows):
    values = [value for header, value in rows if header.casefold() in {"weight", "weight kg"}]
    candidates = []
    range_pattern = re.compile(r"(\d+(?:\.\d+)?)\s*(?:-|–|—|to)\s*(\d+(?:\.\d+)?)\s*kg\b", re.I)
    single_pattern = re.compile(r"\b(\d+(?:\.\d+)?)\s*kg\b", re.I)
    for value in values:
        ranges = range_pattern.findall(value)
        if ranges:
            candidates.extend((float(low), float(high)) for low, high in ranges)
            continue
        singles = single_pattern.findall(value)
        if len(singles) == 1:
            number = float(singles[0])
            candidates.append((number, number))
    if len(candidates) != 1:
        return "", ""
    low, high = candidates[0]
    if low <= 0 or high < low:
        return "", ""
    return format_number(low), format_number(high)


def extract_size(rows):
    values = [value.casefold() for header, value in rows if header.casefold() == "size"]
    mapped = set()
    for value in values:
        if value.strip() in {"small", "medium", "large"}:
            mapped.add(value.strip().upper())
    return mapped.pop() if len(mapped) == 1 else ""


def format_number(value):
    return str(int(value)) if value.is_integer() else str(value)


def extract_record(body, item, config, retrieved_at):
    parser = BreedPageParser()
    try:
        parser.feed(body.decode("utf-8", errors="replace"))
        parser.close()
    except Exception:
        # HTMLParser is tolerant, but malformed content must never crash a batch.
        pass
    source_breed_name = normalize_text(" ".join(parser.title_parts))
    description_raw = ""
    for paragraph in parser.paragraphs:
        normalized = paragraph.casefold()
        is_page_status = normalized.startswith("this is an accepted version") or normalized.startswith(
            "this is the stable version"
        )
        if len(paragraph) >= 20 and not is_page_status and not has_forbidden_content(paragraph):
            description_raw = paragraph[:300].rstrip()
            break
    min_weight_kg, max_weight_kg = extract_weight_kg(parser.rows)
    return {
        "source_name": config["source_name"],
        "license_attribution_note": config["license_attribution_note"],
        "source_url": item["url"],
        "retrieved_at": retrieved_at,
        "content_sha256": sha256_bytes(body),
        "breed_code": item["breed_code"],
        "breed_name": item["breed_name"],
        "source_breed_name": source_breed_name,
        "description_raw": description_raw,
        "default_size": extract_size(parser.rows),
        "min_weight_kg": min_weight_kg,
        "max_weight_kg": max_weight_kg,
    }


def wait_for_rate_limit(last_request_monotonic):
    if last_request_monotonic[0] is None:
        return None, 0.0
    elapsed = time.monotonic() - last_request_monotonic[0]
    wait_seconds = max(0.0, MIN_REQUEST_INTERVAL_SECONDS - elapsed)
    if wait_seconds:
        time.sleep(wait_seconds)
    return elapsed + wait_seconds, wait_seconds


def retry_after_seconds(headers):
    value = headers.get("Retry-After", "") if headers else ""
    return int(value) if value.isdigit() else 0


def fetch_article(url, config, last_request_monotonic, request_log):
    ensure_allowlisted(url, config)
    opener = build_opener(NoRedirectHandler())
    current_url = url
    redirects = 0
    for attempt in range(MAX_RETRIES + 1):
        interval_since_previous, waited = wait_for_rate_limit(last_request_monotonic)
        started = time.monotonic()
        last_request_monotonic[0] = started
        request = Request(current_url, headers={"User-Agent": USER_AGENT, "Accept": "text/html"})
        try:
            response = opener.open(request, timeout=max(CONNECT_TIMEOUT_SECONDS, READ_TIMEOUT_SECONDS))
            status = response.getcode()
            content_type = response.headers.get_content_type()
            body = response.read(MAX_RESPONSE_BYTES + 1)
            duration_ms = round((time.monotonic() - started) * 1000)
        except HTTPError as exc:
            status = exc.code
            content_type = exc.headers.get_content_type() if exc.headers else ""
            location = exc.headers.get("Location") if exc.headers else ""
            duration_ms = round((time.monotonic() - started) * 1000)
            request_log.append({
                "source_url": current_url,
                "attempt": attempt + 1,
                "http_status": status,
                "content_type": content_type,
                "duration_ms": duration_ms,
                "rate_limit_wait_seconds": round(waited, 3),
                "interval_since_previous_request_seconds": (
                    round(interval_since_previous, 3) if interval_since_previous is not None else None
                ),
                "outcome": "http_error",
            })
            if status in {401, 403}:
                raise BatchStopError(f"HTTP {status}; batch stopped") from exc
            if 300 <= status < 400 and location:
                current_url = validate_redirect(current_url, location, config)
                redirects += 1
                if redirects > 1:
                    raise CrawlError("Too many redirects for an allowlisted article")
                continue
            if status in RETRIABLE_STATUS_CODES and attempt < MAX_RETRIES:
                delay = retry_after_seconds(exc.headers) or (2 ** attempt)
                time.sleep(delay)
                continue
            raise CrawlError(f"HTTP {status} for {current_url}") from exc
        except URLError as exc:
            duration_ms = round((time.monotonic() - started) * 1000)
            request_log.append({
                "source_url": current_url,
                "attempt": attempt + 1,
                "http_status": None,
                "content_type": "",
                "duration_ms": duration_ms,
                "rate_limit_wait_seconds": round(waited, 3),
                "interval_since_previous_request_seconds": (
                    round(interval_since_previous, 3) if interval_since_previous is not None else None
                ),
                "outcome": "network_error",
            })
            if attempt < MAX_RETRIES:
                time.sleep(2 ** attempt)
                continue
            raise CrawlError(f"Network error for {current_url}: {exc.reason}") from exc

        request_log.append({
            "source_url": current_url,
            "attempt": attempt + 1,
            "http_status": status,
            "content_type": content_type,
            "duration_ms": duration_ms,
            "rate_limit_wait_seconds": round(waited, 3),
            "interval_since_previous_request_seconds": (
                round(interval_since_previous, 3) if interval_since_previous is not None else None
            ),
            "response_bytes": len(body),
            "outcome": "received",
        })
        if status in {401, 403}:
            raise BatchStopError(f"HTTP {status}; batch stopped")
        if content_type != "text/html":
            raise CrawlError(f"Unexpected content type for {current_url}: {content_type}")
        if len(body) > MAX_RESPONSE_BYTES:
            raise CrawlError(f"Response exceeded 2 MB limit for {current_url}")
        return body
    raise CrawlError(f"Retry limit reached for {url}")


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def write_jsonl(path, records):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        for record in records:
            handle.write(json.dumps(record, ensure_ascii=False, sort_keys=True) + "\n")


def write_report(path, manifest, records, request_log):
    report = [
        "# PawConnect - Controlled Breed Crawl Validation",
        "",
        f"- run_id: `{manifest['run_id']}`",
        f"- source: `{manifest['source_name']}`",
        f"- fetched: `{manifest['fetched']}`",
        f"- accepted: `{manifest['accepted']}`",
        f"- skipped: `{manifest['skipped']}`",
        f"- duplicates: `{manifest['duplicates']}`",
        f"- curated_breeds_unchanged: `{manifest['curated_breeds_unchanged']}`",
        "",
        "## Controls",
        "",
        "| Check | Result |",
        "| --- | --- |",
        "| Exact article allowlist | PASS |",
        "| Robots policy snapshot approved | PASS |",
        "| Same-domain redirect guard | PASS |",
        "| text/html and 2 MB response limit | PASS |",
        "| 2-second per-domain rate limit | PASS |" if manifest["rate_limit_pass"] else "| 2-second per-domain rate limit | FAIL |",
        "| No HTML body, image URL, PII/contact, account, token or API key in raw records | PASS |",
        "| Curated breeds.csv unchanged | PASS |" if manifest["curated_breeds_unchanged"] else "| Curated breeds.csv unchanged | FAIL |",
        "",
        "## Output",
        "",
        f"- records.jsonl: `{len(records)}` records",
        f"- request-log.jsonl: `{len(request_log)}` entries",
        "- Raw descriptions are capped at 300 characters. No cleaner, dedup pipeline, candidate/curated merge or DB import was executed.",
        "",
        "## Conclusion: PASS" if manifest["curated_breeds_unchanged"] and manifest["rate_limit_pass"] else "## Conclusion: FAIL",
        "",
    ]
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(report), encoding="utf-8")


def run_crawl(root, config_path, run_id=None, dry_run=False):
    config = read_json(config_path)
    curated_breeds = load_curated_breed_codes(root)
    validate_config(config, curated_breeds)
    items = config["allowlist"]
    if dry_run:
        print(f"DRY RUN PASS: {len(items)} allowlisted Wikipedia article requests planned; no network call made")
        return None

    resolved_run_id = run_id or datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    if not re.fullmatch(r"[0-9]{8}T[0-9]{6}Z", resolved_run_id):
        raise CrawlError("run_id must use YYYYMMDDTHHMMSSZ")
    output_dir = root / "data-pipeline" / "data" / "raw" / "breeds" / "wikipedia" / resolved_run_id
    if output_dir.exists():
        raise CrawlError(f"Raw batch already exists and is immutable: {output_dir}")

    curated_path = root / "data-pipeline" / "data" / "curated" / "catalog" / "breeds.csv"
    curated_before = sha256_file(curated_path)
    records = []
    request_log = []
    skipped = []
    duplicates = 0
    seen_urls = set()
    seen_checksums = set()
    last_request_monotonic = [None]

    try:
        for item in items:
            if item["url"] in seen_urls:
                duplicates += 1
                skipped.append({"source_url": item["url"], "reason": "duplicate_source_url"})
                continue
            seen_urls.add(item["url"])
            try:
                body = fetch_article(item["url"], config, last_request_monotonic, request_log)
                record = extract_record(body, item, config, utc_now())
                if record["content_sha256"] in seen_checksums:
                    duplicates += 1
                    skipped.append({"source_url": item["url"], "reason": "duplicate_content_sha256"})
                    continue
                seen_checksums.add(record["content_sha256"])
                records.append(record)
                request_log[-1]["outcome"] = "accepted"
            except BatchStopError:
                raise
            except CrawlError as exc:
                skipped.append({"source_url": item["url"], "reason": str(exc)})
    finally:
        curated_after = sha256_file(curated_path)
        intervals = [
            entry["interval_since_previous_request_seconds"]
            for entry in request_log
            if entry.get("interval_since_previous_request_seconds") is not None
        ]
        manifest = {
            "run_id": resolved_run_id,
            "source_name": config["source_name"],
            "source_base_url": config["source_base_url"],
            "license_attribution_note": config["license_attribution_note"],
            "robots_policy": config["robots_policy"],
            "retrieved_at": utc_now(),
            "allowlist_count": len(items),
            "fetched": len(records) + len(skipped),
            "accepted": len(records),
            "skipped": len(skipped),
            "duplicates": duplicates,
            "skip_reasons": skipped,
            "curated_breeds_sha256_before": curated_before,
            "curated_breeds_sha256_after": curated_after,
            "curated_breeds_unchanged": curated_before == curated_after,
            "rate_limit_pass": all(interval >= MIN_REQUEST_INTERVAL_SECONDS for interval in intervals),
            "html_body_saved": False,
        }
        write_json(output_dir / "manifest.json", manifest)
        write_jsonl(output_dir / "records.jsonl", records)
        write_jsonl(output_dir / "request-log.jsonl", request_log)
        write_report(root / "data-pipeline" / "reports" / "breed_crawl_validation.md", manifest, records, request_log)

    if not manifest["curated_breeds_unchanged"] or not manifest["rate_limit_pass"]:
        raise CrawlError("Crawl validation failed after writing raw batch")
    print(
        "PASS controlled breed crawl: "
        f"fetched={manifest['fetched']} accepted={manifest['accepted']} skipped={manifest['skipped']}"
    )
    print(f"Raw batch: {output_dir.relative_to(root).as_posix()}")
    return manifest


def run_self_test(root, config_path):
    config = read_json(config_path)
    curated_breeds = load_curated_breed_codes(root)
    validate_config(config, curated_breeds)
    try:
        ensure_allowlisted("https://en.wikipedia.org/wiki/Beagle", config)
        raise AssertionError("Outside allowlist URL was not rejected")
    except AllowlistError:
        pass
    try:
        validate_redirect(config["allowlist"][0]["url"], "https://example.org/wiki/Poodle", config)
        raise AssertionError("Outside domain redirect was not rejected")
    except AllowlistError:
        pass
    malformed = extract_record(b"<html><p>broken", config["allowlist"][0], config, "2026-09-08T00:00:00Z")
    if malformed["description_raw"] != "":
        raise AssertionError("Malformed short HTML should leave description blank")
    denied_config = dict(config)
    denied_config["robots_policy"] = dict(config["robots_policy"], status="denied")
    try:
        validate_config(denied_config, curated_breeds)
        raise AssertionError("Denied robots policy was not rejected")
    except BatchStopError:
        pass
    print("PASS self-test: dry-run path, allowlist, redirect guard, malformed HTML and robots deny")


def main():
    root = project_root()
    parser = argparse.ArgumentParser(description="Run the controlled PawConnect Wikipedia breed raw crawler.")
    parser.add_argument(
        "--config",
        type=Path,
        default=root / "data-pipeline" / "config" / "sources.json",
        help="Approved source configuration.",
    )
    parser.add_argument("--run-id", help="Immutable raw batch id in YYYYMMDDTHHMMSSZ format.")
    parser.add_argument("--dry-run", action="store_true", help="Validate the plan without network access or file output.")
    parser.add_argument("--self-test", action="store_true", help="Run offline guard tests without network access.")
    args = parser.parse_args()

    try:
        if args.self_test:
            run_self_test(root, args.config)
        else:
            run_crawl(root, args.config, args.run_id, args.dry_run)
        return 0
    except CrawlError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1
    except AssertionError as exc:
        print(f"FAIL self-test: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
