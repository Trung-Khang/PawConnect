"""Controlled commercial observation crawler for PawConnect."""

import argparse
import csv
import hashlib
import json
import re
import sys
import time
import unicodedata
from datetime import datetime, timezone
from html import unescape
from html.parser import HTMLParser
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, urlencode, urljoin, urlparse, urlunparse
from urllib.request import HTTPRedirectHandler, Request, build_opener


OUTPUT_HEADER = [
    "source_url", "store_name", "retrieved_at", "source_category", "breed_label",
    "breed_type", "age_months", "current_weight_kg", "current_size", "brand_label",
    "public_price_vnd", "availability_label", "checksum",
]
USER_AGENT = "PawConnect-CommercialObservation/0.1"
MAX_RESPONSE_BYTES = 2 * 1024 * 1024
REQUEST_TIMEOUT_SECONDS = 10
MIN_REQUEST_INTERVAL_SECONDS = 2.0
MAX_RETRIES = 2
ALLOWED_BREED_TYPES = {"PUREBRED", "MIXED", "UNKNOWN"}
ALLOWED_CATEGORIES = {"PUPPY_MARKETPLACE", "PRODUCT_CATALOG"}
RETRIABLE_STATUS = {408, 429, 500, 502, 503, 504}


class CrawlError(Exception):
    """Controlled crawler failure."""


class NoRedirectHandler(HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, new_url):
        return None


class PageParser(HTMLParser):
    """Collect visible text, links, and JSON-LD without media or forms."""

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.text_parts = []
        self.links = []
        self.json_blocks = []
        self._skip_depth = 0
        self._form_depth = 0
        self._link = None
        self._script_type = ""
        self._script_parts = []

    def handle_starttag(self, tag, attrs):
        attr = dict(attrs)
        if tag == "script":
            self._script_type = attr.get("type", "")
            self._script_parts = []
            return
        if tag in {"style", "noscript", "svg", "img", "video", "audio", "iframe"}:
            self._skip_depth += 1
            return
        if tag == "form":
            self._form_depth += 1
            return
        if self._skip_depth or self._form_depth:
            return
        if tag == "a" and attr.get("href"):
            self._link = {"href": attr["href"], "text": []}

    def handle_endtag(self, tag):
        if tag == "script":
            if self._script_type in {"application/ld+json", "application/json"} or self._script_parts:
                self.json_blocks.append("".join(self._script_parts))
            self._script_type = ""
            self._script_parts = []
            return
        if tag in {"style", "noscript", "svg", "img", "video", "audio", "iframe"}:
            self._skip_depth = max(0, self._skip_depth - 1)
            return
        if tag == "form":
            self._form_depth = max(0, self._form_depth - 1)
            return
        if self._skip_depth or self._form_depth:
            return
        if tag == "a" and self._link is not None:
            self._link["text"] = normalize_text(" ".join(self._link["text"]))
            self.links.append(self._link)
            self._link = None

    def handle_data(self, data):
        if self._script_type:
            self._script_parts.append(data)
            return
        if self._skip_depth or self._form_depth:
            return
        value = normalize_text(data)
        if value:
            self.text_parts.append(value)
            if self._link is not None:
                self._link["text"].append(value)


def project_root():
    return Path(__file__).resolve().parents[3]


def normalize_text(value):
    return re.sub(r"\s+", " ", unescape(value or "")).strip()


def ascii_fold(value):
    return "".join(char for char in unicodedata.normalize("NFD", value) if unicodedata.category(char) != "Mn").casefold()


def utc_now():
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def read_json(path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise CrawlError(f"Missing configuration: {path}") from exc
    except json.JSONDecodeError as exc:
        raise CrawlError(f"Invalid JSON configuration: {exc}") from exc


def source_is_approved(source):
    return all(source.get(field) == "APPROVED" for field in ("permission_status", "robots_status", "terms_status"))


def validate_config(config):
    if config.get("network_default") != "disabled":
        raise CrawlError("network_default must remain disabled")
    sources = config.get("sources")
    if not isinstance(sources, list) or not sources:
        raise CrawlError("sources must be a non-empty list")
    for source in sources:
        required = {"source_name", "domain", "catalog_url", "page_url_pattern", "source_category", "max_pages", "permission_status", "robots_status", "terms_status", "enabled"}
        missing = sorted(required - set(source))
        if missing:
            raise CrawlError(f"Source missing fields: {', '.join(missing)}")
        if source["source_category"] not in ALLOWED_CATEGORIES:
            raise CrawlError(f"Invalid source category: {source['source_category']}")
        parsed = urlparse(source["catalog_url"])
        if parsed.scheme != "https" or parsed.netloc != source["domain"]:
            raise CrawlError(f"Catalog URL does not match domain: {source['catalog_url']}")
        if "{page}" not in source["page_url_pattern"]:
            raise CrawlError(f"page_url_pattern must contain {{page}}: {source['source_name']}")
        if not isinstance(source["max_pages"], int) or source["max_pages"] < 1:
            raise CrawlError(f"max_pages must be a positive integer: {source['source_name']}")
        if source["enabled"] and not source_is_approved(source):
            raise CrawlError(f"Enabled source is not approved: {source['source_name']}")


def get_source(config, name):
    matches = [source for source in config["sources"] if source["source_name"] == name]
    if len(matches) != 1:
        raise CrawlError(f"Unknown or duplicate source: {name}")
    source = matches[0]
    if not source["enabled"] or not source_is_approved(source):
        raise CrawlError(f"Source is locked: {name}")
    return source


def fetch_html(url, domain, last_request, request_log):
    current_url = url
    for attempt in range(MAX_RETRIES + 1):
        waited = 0.0
        if last_request[0] is not None:
            waited = max(0.0, MIN_REQUEST_INTERVAL_SECONDS - (time.monotonic() - last_request[0]))
            if waited:
                time.sleep(waited)
        last_request[0] = time.monotonic()
        request = Request(current_url, headers={"User-Agent": USER_AGENT, "Accept": "text/html"})
        opener = build_opener(NoRedirectHandler())
        try:
            response = opener.open(request, timeout=REQUEST_TIMEOUT_SECONDS)
        except HTTPError as exc:
            request_log.append({"url": current_url, "status": exc.code, "attempt": attempt + 1, "wait_seconds": round(waited, 3)})
            if exc.code in {401, 403}:
                raise CrawlError(f"HTTP {exc.code}; source stopped") from exc
            if exc.code in {301, 302, 303, 307, 308} and exc.headers.get("Location"):
                target = urljoin(current_url, exc.headers["Location"])
                if not same_domain(target, domain):
                    raise CrawlError(f"Cross-domain redirect blocked: {target}")
                current_url = target
                continue
            if exc.code in RETRIABLE_STATUS and attempt < MAX_RETRIES:
                time.sleep(min(10, 2 ** attempt))
                continue
            raise CrawlError(f"HTTP {exc.code}: {current_url}") from exc
        except URLError as exc:
            request_log.append({"url": url, "status": "network_error", "attempt": attempt + 1, "wait_seconds": round(waited, 3)})
            if attempt < MAX_RETRIES:
                time.sleep(min(10, 2 ** attempt))
                continue
            raise CrawlError(f"Network error: {exc.reason}") from exc
        location = response.headers.get("Location")
        if location:
            target = urljoin(current_url, location)
            if not same_domain(target, domain):
                raise CrawlError(f"Cross-domain redirect blocked: {target}")
            current_url = target
            continue
        if response.headers.get_content_type() != "text/html":
            raise CrawlError(f"Expected text/html: {current_url}")
        body = response.read(MAX_RESPONSE_BYTES + 1)
        request_log.append({"url": current_url, "status": response.status, "attempt": attempt + 1, "bytes": len(body)})
        if len(body) > MAX_RESPONSE_BYTES:
            raise CrawlError(f"Response exceeded 2 MB: {current_url}")
        return body
    raise CrawlError(f"Retry limit reached: {url}")


def same_domain(url, domain):
    host = urlparse(url).netloc.casefold().split(":", 1)[0]
    expected = domain.casefold().split(":", 1)[0]
    return host == expected or host.removeprefix("www.") == expected.removeprefix("www.")


def parse_json_blocks(blocks):
    values = []
    for block in blocks:
        try:
            values.append(json.loads(block))
        except json.JSONDecodeError:
            continue
    return values


def walk_dicts(value):
    if isinstance(value, dict):
        yield value
        for child in value.values():
            yield from walk_dicts(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk_dicts(child)


def value_text(value):
    if isinstance(value, dict):
        return value.get("name") or value.get("title") or value.get("label") or ""
    return str(value or "")


def price_value(value):
    text = normalize_text(value_text(value))
    numbers = re.findall(r"(?<!\d)(\d{1,3}(?:[.,]\d{3})+|\d+)(?:\s*(?:đ|₫|vnd))?\b", text, re.I)
    if len(numbers) != 1:
        return ""
    raw = numbers[0].replace(".", "").replace(",", "")
    number = int(raw)
    return str(number) if number > 0 else ""


def age_months(text):
    normalized = ascii_fold(text)
    matches = re.findall(r"(?<!\d)(?:duoi\s+|hon\s+)?(\d+)\s*thang(?:\s*tuoi)?", normalized)
    if len(matches) == 1:
        return matches[0]
    matches = re.findall(r"(?<!\d)(?:duoi\s+|hon\s+)?(\d+)\s*tuoi", normalized)
    if len(matches) == 1:
        return str(int(matches[0]) * 12)
    return ""


def breed_type(text):
    normalized = ascii_fold(text)
    if "thuan chung" in normalized:
        return "PUREBRED"
    if re.search(r"\blai\b", normalized):
        return "MIXED"
    return ""


def make_record(source, url, retrieved_at, **values):
    item = {field: "" for field in OUTPUT_HEADER}
    item.update({"source_url": url, "store_name": source["source_name"], "retrieved_at": retrieved_at, "source_category": source["source_category"]})
    for key, value in values.items():
        if key in item and value is not None:
            item[key] = normalize_text(str(value))
    stable = "|".join(item[field] for field in ("source_url", "breed_label", "brand_label", "public_price_vnd"))
    item["checksum"] = hashlib.sha256(stable.encode("utf-8")).hexdigest()
    return item


def product_objects(parser):
    objects = []
    for data in parse_json_blocks(parser.json_blocks):
        for item in walk_dicts(data):
            item_type = str(item.get("@type", "")).casefold()
            if "product" in item_type or {"name", "offers"}.issubset(item):
                objects.append(item)
    return objects


def product_records(parser, source, url, retrieved_at):
    records = []
    for item in product_objects(parser):
        item_url = item.get("url") or item.get("@id")
        if not item_url:
            continue
        item_url = urljoin(url, str(item_url))
        if not same_domain(item_url, source["domain"]):
            continue
        offers = item.get("offers", {})
        if isinstance(offers, list):
            offers = offers[0] if offers else {}
        offers = offers if isinstance(offers, dict) else {}
        availability = str(offers.get("availability", ""))
        if "outofstock" in availability.casefold():
            availability = "out_of_stock"
        elif availability:
            availability = "in_stock"
        brand = item.get("brand", {})
        records.append(make_record(source, item_url, retrieved_at, brand_label=value_text(brand), public_price_vnd=price_value(offers.get("price", "")), availability_label=availability))
    return records


class ChototListingParser(HTMLParser):
    """Capture candidate listing blocks and visible title links only."""

    BLOCK_TAGS = {"li", "article", "div"}

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.blocks = []
        self._stack = []
        self._link = None
        self._skip_depth = 0

    @staticmethod
    def _is_block(tag, attrs):
        if tag not in ChototListingParser.BLOCK_TAGS:
            return False
        attr = dict(attrs)
        marker = f"{attr.get('class', '')} {attr.get('id', '')}".casefold()
        return bool(re.search(r"(?:^|[\s_-])(aditem|ad|item)(?:[\s_-]|$)", marker))

    def handle_starttag(self, tag, attrs):
        if self._skip_depth:
            self._skip_depth += 1
            return
        if tag in {"script", "style", "noscript", "svg", "img", "video", "audio", "iframe", "form"}:
            self._skip_depth = 1
            return
        is_block = self._is_block(tag, attrs)
        if is_block:
            self._stack.append({"tag": tag, "depth": 1, "text": [], "links": []})
        elif self._stack:
            for block in self._stack:
                block["depth"] += 1
        if tag == "a" and self._stack:
            attr = dict(attrs)
            if attr.get("href"):
                self._link = {"href": attr["href"], "text": []}

    def handle_endtag(self, tag):
        if self._skip_depth:
            self._skip_depth -= 1
            return
        if self._link is not None and tag == "a":
            self._link["text"] = normalize_text(" ".join(self._link["text"]))
            if self._stack:
                self._stack[-1]["links"].append(self._link)
            self._link = None
        if not self._stack:
            return
        for block in self._stack:
            block["depth"] = max(0, block["depth"] - 1)
        if self._stack[-1]["depth"] == 0 and self._stack[-1]["tag"] == tag:
            block = self._stack.pop()
            block["text"] = normalize_text(" ".join(block["text"]))
            self.blocks.append(block)

    def handle_data(self, data):
        if self._skip_depth or not self._stack:
            return
        value = normalize_text(data)
        if value:
            self._stack[-1]["text"].append(value)
            if self._link is not None:
                self._link["text"].append(value)


def chotot_json_records(data, source, url, retrieved_at, url_map):
    records = []
    for item in walk_dicts(data):
        title = item.get("subject") or item.get("title") or item.get("ad_title")
        price = item.get("price_string") or item.get("price") or item.get("list_price")
        item_url = item.get("url") or item.get("ad_link") or item.get("canonical_url")
        if not item_url:
            item_url = url_map.get(str(item.get("list_id", "")), "")
        if not title or price is None or not item_url:
            continue
        title = value_text(title)
        price_number = price_value(price)
        if not price_number:
            continue
        detail_url = urljoin(url, str(item_url))
        if not same_domain(detail_url, source["domain"]):
            continue
        details = title
        for param in item.get("params", []):
            if isinstance(param, dict):
                details += " " + value_text(param.get("value"))
        records.append(make_record(
            source,
            detail_url,
            retrieved_at,
            breed_label=title,
            breed_type=breed_type(title) or "UNKNOWN",
            age_months=age_months(details),
            public_price_vnd=price_number,
            availability_label="available",
        ))
    return records


def chotot_html_records(html, source, url, retrieved_at):
    parser = ChototListingParser()
    parser.feed(html.decode("utf-8", errors="replace"))
    records = []
    for block in parser.blocks:
        detail = ""
        title = ""
        for link in block["links"]:
            target = urljoin(url, link["href"])
            path = urlparse(target).path
            if link["text"] and same_domain(target, source["domain"]):
                if ".htm" in path or "/mua-ban-cho" in path:
                    detail, title = target, link["text"]
                    break
        price = price_value(block["text"])
        if not detail or not title or not price:
            continue
        records.append(make_record(
            source,
            detail,
            retrieved_at,
            breed_label=title,
            breed_type=breed_type(title) or "UNKNOWN",
            age_months=age_months(block["text"]),
            public_price_vnd=price,
            availability_label="available",
        ))
    return records


def parse_chotot(html, source, url, retrieved_at):
    parser = PageParser()
    parser.feed(html.decode("utf-8", errors="replace"))
    data_blocks = parse_json_blocks(parser.json_blocks)
    url_map = {}
    for data in data_blocks:
        for item in walk_dicts(data):
            if item.get("@type") != "ListItem" or not item.get("url"):
                continue
            match = re.search(r"/(\d+)\.htm(?:$|[?#])", str(item["url"]))
            if match:
                url_map[match.group(1)] = urljoin(url, str(item["url"]))
    records = []
    for data in data_blocks:
        records.extend(chotot_json_records(data, source, url, retrieved_at, url_map))
    if not records:
        records.extend(chotot_html_records(html, source, url, retrieved_at))
    return dedupe(records)


def parse_petmart(html, source, url, retrieved_at):
    parser = PageParser()
    parser.feed(html.decode("utf-8", errors="replace"))
    return dedupe(product_records(parser, source, url, retrieved_at))


def parse_paddy(html, source, url, retrieved_at):
    parser = PageParser()
    parser.feed(html.decode("utf-8", errors="replace"))
    return dedupe(product_records(parser, source, url, retrieved_at))


def category_urls(parser, source, base_url):
    results = []
    for link in parser.links:
        target = urljoin(base_url, link["href"])
        parsed = urlparse(target)
        if parsed.scheme != "https" or not same_domain(target, source["domain"]):
            continue
        path = parsed.path.casefold()
        if source["source_name"] == "Pet Mart":
            matches = "/danh-muc/" in path and "cho" in path
        else:
            matches = "/cho" in path and path.rstrip("/") != "/cho"
        if matches and target not in results:
            results.append(target)
    return results


def dedupe(records):
    unique = {}
    for item in records:
        key = (item["source_url"], item["breed_label"], item["brand_label"], item["public_price_vnd"])
        unique[key] = item
    return list(unique.values())


def next_page_url(url, page):
    parsed = urlparse(url)
    query = parse_qs(parsed.query, keep_blank_values=True)
    query["page"] = [str(page)]
    return urlunparse(parsed._replace(query=urlencode(query, doseq=True)))


def parser_for(source_name):
    if source_name == "Pet Mart":
        return parse_petmart
    return parse_chotot


def build_page_url(source, page):
    if page == 1:
        return source["catalog_url"]
    return source["page_url_pattern"].format(page=page)


def crawl_source(source, max_pages):
    parser = parser_for(source["source_name"])
    records = []
    request_log = []
    last_request = [None]
    page = 0
    while page < max_pages:
        page += 1
        url = build_page_url(source, page)
        body = fetch_html(url, source["domain"], last_request, request_log)
        page_records = parser(body, source, url, utc_now())
        records.extend(page_records)
    return dedupe(records), request_log


def validate_record(item):
    if list(item) != OUTPUT_HEADER:
        raise CrawlError("Output header mismatch")
    if item["source_category"] not in ALLOWED_CATEGORIES:
        raise CrawlError("Invalid source_category")
    if item["breed_type"] and item["breed_type"] not in ALLOWED_BREED_TYPES:
        raise CrawlError("Invalid breed_type")
    if any(field in item for field in ("phone", "email", "address", "image_url", "token", "api_key")):
        raise CrawlError("Forbidden field")


def write_csv(path, records):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=OUTPUT_HEADER)
        writer.writeheader()
        writer.writerows(records)


def default_output(root, source_name):
    slug = re.sub(r"[^a-z0-9]+", "_", ascii_fold(source_name)).strip("_")
    return root / "data-pipeline" / "data" / "raw" / "commercial_observations" / f"{slug}.csv"


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    root = project_root()
    parser = argparse.ArgumentParser(description="Crawl approved PawConnect commercial observation sources.")
    parser.add_argument("--config", type=Path, default=root / "data-pipeline" / "config" / "commercial_sources.json")
    parser.add_argument("--source", required=True, choices=["Chợ Tốt", "Chợ Tốt (cho-giong)", "Pet Mart"])
    parser.add_argument("--max-pages", type=int, help="Override the source max_pages value.")
    parser.add_argument("--output", type=Path, help="CSV output path; defaults to a source-specific raw file.")
    parser.add_argument("--allow-network", action="store_true")
    args = parser.parse_args()
    if args.max_pages is not None and args.max_pages < 1:
        parser.error("--max-pages must be >= 1")
    try:
        config = read_json(args.config)
        validate_config(config)
        source = get_source(config, args.source)
        if not args.allow_network:
            raise CrawlError("Network disabled; pass --allow-network after source approval")
        max_pages = args.max_pages if args.max_pages is not None else source["max_pages"]
        if max_pages < 1:
            raise CrawlError("--max-pages must be >= 1")
        records, request_log = crawl_source(source, max_pages)
        for item in records:
            validate_record(item)
        write_csv(args.output or default_output(root, args.source), records)
        print(f"PASS source={args.source} pages<={max_pages} records={len(records)} requests={len(request_log)}")
        return 0
    except (CrawlError, OSError) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
