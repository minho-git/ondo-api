# ─────────────────────────────────────────────────────────────
# GitHub Actions 가 배포하러 들어오는 문 (MUL-105)
#
# 지금까지 배포는 내 노트북에서 했다. AWS 자격증명이 노트북에 있어서 가능했던
# 건데, 그래서 다른 사람은 배포를 못 하고 저장소와 배포가 벌어졌다.
#
# 액세스 키를 GitHub 에 넣어두는 방법도 있지만 안 쓴다. 키는 만료가 없어서
# 한 번 새면 계속 유효하고, 샜는지 알 방법도 없다.
#
# 대신 OIDC 다. GitHub 이 「이 저장소의 이 브랜치에서 도는 작업이다」라는 증명서를
# 그때그때 발급하고, AWS 가 그걸 확인하고 짧은 임시 자격증명을 내준다.
# 저장소에 남는 비밀이 하나도 없다 — 티켓의 완료조건이 그거다.
# ─────────────────────────────────────────────────────────────

resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]

  # thumbprint_list 를 안 적었다. GitHub 처럼 널리 쓰는 곳은 AWS 가 자기
  # 신뢰 루트로 검증한다. 예전엔 인증서 지문을 손으로 박아뒀는데, 그게 갱신될
  # 때마다 배포가 통째로 깨지는 원인이었다
}

variable "github_deploy_subjects" {
  description = "이 역할을 맡을 수 있는 GitHub 작업"
  type        = list(string)

  # 이 저장소의 브랜치에서 도는 작업만 통과한다.
  #
  # ⚠️ 이름 뒤의 @숫자를 빼면 안 된다. 우리 조직은 sub 에 불변 ID 가 붙어서 온다 —
  #    실제로 받은 값이 이렇다.
  #
  #      repo:ondo-commerce@312365889/ondo-api@1321296842:ref:refs/heads/dev
  #
  #    처음에 ID 없이 걸었다가 「Not authorized to perform
  #    sts:AssumeRoleWithWebIdentity」로 배포가 통째로 막혔다. STS 는 왜 안 맞는지
  #    안 알려주므로, 토큰 클레임을 직접 찍어서 형식을 확인했다.
  #
  #    ID 를 쓰는 게 이름보다 안전하다. 조직·저장소 이름은 바꿀 수 있어서,
  #    이름만 믿으면 누가 그 이름을 차지해 신뢰를 가로챌 수 있다. ID 는 안 바뀐다.
  #
  # 두 형식을 다 넣는다. GitHub 이 ID 를 안 붙이는 쪽으로 돌아가도 배포가 안 막힌다.
  # 둘 다 조직·저장소를 정확히 지목하므로 넓어지는 게 아니다.
  #
  # 브랜치는 * 로 열어 뒀다. dev 하나로 더 좁힐 수 있지만 그러면 피처 브랜치에서
  # 워크플로를 시험해 볼 수가 없다. 비공개 저장소고 푸시할 수 있는 사람이 셋뿐이다.
  #
  # 남의 포크에서 올린 PR 은 어느 쪽에도 안 걸린다 — 그쪽은 소유자가 다르다
  default = [
    "repo:ondo-commerce@312365889/ondo-api@1321296842:ref:refs/heads/*",
    "repo:ondo-commerce/ondo-api:ref:refs/heads/*",
  ]
}

data "aws_iam_policy_document" "github_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    # 증명서가 AWS 앞으로 발급된 것인지
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    # ⚠️ 이 파일에서 제일 중요한 줄이다.
    # 이 조건이 없으면 세상 모든 GitHub 저장소가 이 역할을 맡을 수 있다
    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = var.github_deploy_subjects
    }
  }
}

resource "aws_iam_role" "github_deploy" {
  name               = "${local.prefix}-github-deploy"
  assume_role_policy = data.aws_iam_policy_document.github_assume.json
  tags               = { Name = "${local.prefix}-github-deploy" }
}

# ── 이 역할이 할 수 있는 일 ──────────────────────────────────
#
# 배포에 필요한 것만 준다. 인프라는 못 건드린다 — RDS · VPC · ALB 는 물론이고
# 태스크 정의의 CPU · 환경변수도 못 만든다. 그건 계속 터라폼(=사람) 담당이다.
data "aws_iam_policy_document" "github_deploy" {

  # 창고에 로그인. 이것만 리소스를 못 좁힌다 — AWS 가 * 만 받는다
  statement {
    sid       = "EcrLogin"
    effect    = "Allow"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  # 통조림 밀어 넣기. 우리 저장소 둘만
  statement {
    sid    = "EcrPush"
    effect = "Allow"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
      "ecr:PutImage",
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer",
    ]
    resources = [
      aws_ecr_repository.retail.arn,
      aws_ecr_repository.wholesale.arn,
    ]
  }

  # 지시서를 읽고 새 리비전을 등록한다.
  #
  # RegisterTaskDefinition 은 리소스를 못 좁힌다 — 아직 없는 것을 만드는
  # 요청이라 지목할 대상이 없다. 대신 아래 PassRole 이 실질적인 울타리가 된다
  statement {
    sid       = "EcsTaskDefinition"
    effect    = "Allow"
    actions   = ["ecs:DescribeTaskDefinition", "ecs:RegisterTaskDefinition"]
    resources = ["*"]
  }

  # 서비스에 새 지시서를 물린다. 우리 서비스 둘만
  statement {
    sid     = "EcsDeploy"
    effect  = "Allow"
    actions = ["ecs:DescribeServices", "ecs:UpdateService"]
    resources = [
      aws_ecs_service.retail.id,
      aws_ecs_service.wholesale.id,
    ]
  }

  # 지시서에는 「이 태스크는 이 역할로 돈다」가 적혀 있다. 그걸 등록하려면
  # 그 역할을 넘길 권한이 필요하다.
  #
  # ⚠️ 넘길 수 있는 역할을 이 넷으로 못박는다. 안 그러면 관리자 역할이 박힌
  #    태스크를 띄워서 권한을 통째로 가져갈 수 있다 — RegisterTaskDefinition 이
  #    * 인 게 여기서 막힌다
  statement {
    sid     = "PassTaskRoles"
    effect  = "Allow"
    actions = ["iam:PassRole"]
    resources = [
      aws_iam_role.exec_retail.arn,
      aws_iam_role.exec_wholesale.arn,
      aws_iam_role.task_retail.arn,
      aws_iam_role.task_wholesale.arn,
    ]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "github_deploy" {
  name   = "${local.prefix}-github-deploy"
  role   = aws_iam_role.github_deploy.id
  policy = data.aws_iam_policy_document.github_deploy.json
}
