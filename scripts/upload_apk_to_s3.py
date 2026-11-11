#!/usr/bin/env python3
"""把构建产物上传到 Supabase Storage 的 S3 兼容接口。

只依赖 Python 标准库，避免在 CI 里安装 awscli。
环境变量：
  S3_ENDPOINT    形如 https://<ref>.storage.supabase.co/storage/v1/s3
  S3_REGION      形如 ap-southeast-2
  S3_BUCKET      形如 anakin-files
  S3_ACCESS_KEY  Supabase 生成的 Access Key
  S3_SECRET_KEY  对应的 Secret Key
用法：
  upload_apk_to_s3.py <apk路径> <对象键>
输出最后一行为公网直链。
"""
import datetime
import hashlib
import hmac
import os
import sys
import urllib.request

CONTENT_TYPE = "application/vnd.android.package-archive"


def _sign(key: bytes, data: str) -> bytes:
    return hmac.new(key, data.encode(), hashlib.sha256).digest()


def build_request(endpoint: str, region: str, bucket: str, obj: str,
                  access_key: str, secret_key: str, body: bytes) -> urllib.request.Request:
    host = endpoint.split("://", 1)[1].split("/")[0]
    path = f"/storage/v1/s3/{bucket}/{obj}"
    now = datetime.datetime.now(datetime.timezone.utc)
    amz_date = now.strftime("%Y%m%dT%H%M%SZ")
    date = amz_date[:8]
    payload_hash = hashlib.sha256(body).hexdigest()

    canonical_headers = (
        f"host:{host}\n"
        f"x-amz-content-sha256:{payload_hash}\n"
        f"x-amz-date:{amz_date}\n"
    )
    signed_headers = "host;x-amz-content-sha256;x-amz-date"
    canonical_request = "\n".join([
        "PUT", path, "", canonical_headers, signed_headers, payload_hash
    ])
    scope = f"{date}/{region}/s3/aws4_request"
    to_sign = "\n".join([
        "AWS4-HMAC-SHA256", amz_date, scope,
        hashlib.sha256(canonical_request.encode()).hexdigest()
    ])

    key = _sign(("AWS4" + secret_key).encode(), date)
    key = _sign(key, region)
    key = _sign(key, "s3")
    key = _sign(key, "aws4_request")
    signature = hmac.new(key, to_sign.encode(), hashlib.sha256).hexdigest()

    authorization = (
        f"AWS4-HMAC-SHA256 Credential={access_key}/{scope}, "
        f"SignedHeaders={signed_headers}, Signature={signature}"
    )
    return urllib.request.Request(
        f"{endpoint}/{bucket}/{obj}",
        data=body,
        method="PUT",
        headers={
            "x-amz-date": amz_date,
            "x-amz-content-sha256": payload_hash,
            "Authorization": authorization,
            "Content-Type": CONTENT_TYPE,
            "Content-Length": str(len(body)),
        },
    )


def main() -> int:
    if len(sys.argv) != 3:
        print("用法: upload_apk_to_s3.py <apk路径> <对象键>", file=sys.stderr)
        return 2

    apk_path, obj = sys.argv[1], sys.argv[2]
    required = ["S3_ENDPOINT", "S3_REGION", "S3_BUCKET", "S3_ACCESS_KEY", "S3_SECRET_KEY"]
    missing = [k for k in required if not os.environ.get(k)]
    if missing:
        print("缺少环境变量: " + ", ".join(missing), file=sys.stderr)
        return 3

    with open(apk_path, "rb") as f:
        body = f.read()

    endpoint = os.environ["S3_ENDPOINT"].rstrip("/")
    req = build_request(
        endpoint,
        os.environ["S3_REGION"],
        os.environ["S3_BUCKET"],
        obj,
        os.environ["S3_ACCESS_KEY"],
        os.environ["S3_SECRET_KEY"],
        body,
    )

    with urllib.request.urlopen(req) as resp:
        print(f"上传状态 {resp.status}  大小 {len(body) / 1048576:.1f} MB")

    host = endpoint.split("://", 1)[1].split("/")[0]
    bucket = os.environ["S3_BUCKET"]
    print(f"PUBLIC_URL=https://{host}/storage/v1/object/public/{bucket}/{obj}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
