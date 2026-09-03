# ─────────────────────────────────────────────────────────────
# C · ECR — 도커 이미지를 두는 곳
#
# 배포 단위가 이미지 하나다. 롤백은 이전 태그로 되돌리면 끝난다 —
# ECS 를 고른 이유가 그거였다(인프라.md 「EC2 + jar 가 아니라 ECS」).
# ─────────────────────────────────────────────────────────────

resource "aws_ecr_repository" "retail" {
  name = "${local.prefix}-retail"

  # 같은 태그로 다시 올리는 걸 막지 않는다. 개발 중엔 latest 를 덮어쓰는 게 편하다.
  # ⚠️ 운영 환경에서는 IMMUTABLE 로 둔다 — 배포된 이미지가 조용히 바뀌면 안 된다
  image_tag_mutability = "MUTABLE"

  # 올릴 때 취약점을 훑는다. 공짜다
  image_scanning_configuration {
    scan_on_push = true
  }

  tags = { Name = "${local.prefix}-retail" }
}

resource "aws_ecr_repository" "wholesale" {
  name                 = "${local.prefix}-wholesale"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = { Name = "${local.prefix}-wholesale" }
}

# ── 오래된 이미지 정리 ───────────────────────────────────────
#
# 이미지가 쌓이면 스토리지 요금이 붙는다. 배포할 때마다 하나씩 늘어나므로
# 개발 환경에서는 금방 수십 개가 된다. 최근 10개만 남긴다.
locals {
  ecr_lifecycle = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "최근 10개만 남긴다"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}

resource "aws_ecr_lifecycle_policy" "retail" {
  repository = aws_ecr_repository.retail.name
  policy     = local.ecr_lifecycle
}

resource "aws_ecr_lifecycle_policy" "wholesale" {
  repository = aws_ecr_repository.wholesale.name
  policy     = local.ecr_lifecycle
}
