# ─────────────────────────────────────────────────────────────
# B · S3 — 사용자가 올리는 파일
#
# 두 종류의 성격이 달라서 버킷을 나눈다. 한 버킷에 접두사로 가르면 공개 정책을
# 접두사 단위로 관리해야 하는데, 그건 실수 한 번에 증빙 서류가 열린다.
#
#   상품 이미지    도매가 올리고 소매가 본다.   나중에 공개 읽기로 열 수 있다
#   증빙 서류      소매가 올리고 운영자만 본다.  절대 안 연다
#
# ⚠️ 지금은 둘 다 안 쓰인다. 앱이 LocalFileStorage 로 컨테이너 안에 쓰고 있다.
#    S3 구현체로 갈아끼우는 건 배포 전 할 일이다(MUL-77 「배포 전에 닫을 것」).
#    버킷은 안 쓰면 요금이 없어서 미리 만들어 둔다.
# ─────────────────────────────────────────────────────────────

# 버킷 이름은 전 세계에서 유일해야 한다. 계정 번호를 붙여 겹치지 않게 한다
data "aws_caller_identity" "current" {}

locals {
  bucket_suffix = data.aws_caller_identity.current.account_id
}

# ── 상품 이미지 ──────────────────────────────────────────────
resource "aws_s3_bucket" "listing_images" {
  bucket = "${local.prefix}-listing-images-${local.bucket_suffix}"
  tags   = { Name = "${local.prefix}-listing-images" }
}

# ── 증빙 서류 — 사업자등록증 ─────────────────────────────────
resource "aws_s3_bucket" "documents" {
  bucket = "${local.prefix}-documents-${local.bucket_suffix}"
  tags   = { Name = "${local.prefix}-documents" }
}

# ── 둘 다 닫아 둔다 ──────────────────────────────────────────
#
# 상품 이미지도 지금은 닫는다. 여는 건 실제로 이미지를 서빙할 때 정한다 —
# 버킷을 통째로 여는 것 말고 CloudFront 나 presigned URL 로 가는 길도 있어서,
# 필요해질 때 고르는 게 맞다. 지금 열어두면 쓰지도 않는 게 열려 있게 된다.
resource "aws_s3_bucket_public_access_block" "listing_images" {
  bucket                  = aws_s3_bucket.listing_images.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_public_access_block" "documents" {
  bucket                  = aws_s3_bucket.documents.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# ── 저장할 때 암호화 ─────────────────────────────────────────
# 증빙 서류는 개인정보에 가깝다. 상품 이미지도 같이 켠다 — 공짜고 끌 이유가 없다
resource "aws_s3_bucket_server_side_encryption_configuration" "listing_images" {
  bucket = aws_s3_bucket.listing_images.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "documents" {
  bucket = aws_s3_bucket.documents.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# ── 증빙 서류만 버전 관리 ────────────────────────────────────
#
# 실수로 지우거나 덮어써도 되돌린다. 심사 근거라 없어지면 곤란하다.
# 상품 이미지는 안 켠다 — 도매가 자주 갈아끼우는 파일이라 옛 버전이 계속 쌓인다
resource "aws_s3_bucket_versioning" "documents" {
  bucket = aws_s3_bucket.documents.id

  versioning_configuration {
    status = "Enabled"
  }
}
