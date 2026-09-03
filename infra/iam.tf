# ─────────────────────────────────────────────────────────────
# C · IAM — 태스크가 AWS 를 부를 때 쓰는 권한
#
# 역할이 두 종류다. 헷갈리기 쉬운데 쓰는 주체가 다르다.
#
#   실행 역할(execution)  ECS 가 태스크를 「띄울 때」 쓴다.
#                        이미지를 받고, 시크릿을 읽어 환경변수로 넣고, 로그를 만든다.
#                        앱은 이 역할을 못 쓴다
#
#   태스크 역할(task)     「앱 자신이」 AWS 를 부를 때 쓴다. S3 업로드 같은 것
#
# ⚠️ 실행 역할을 도매·소매로 나눈다. 「읽기 권한도 태스크별로 나눈다 — 소매 서버는
#    도매 DB 비밀번호를 못 읽는다」(인프라.md). 하나로 합치면 그 경계가 무너진다.
# ─────────────────────────────────────────────────────────────

# ECS 가 이 역할을 맡을 수 있게 하는 신뢰 정책. 두 역할이 같이 쓴다
data "aws_iam_policy_document" "ecs_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

# ── 실행 역할 · 소매 ─────────────────────────────────────────
resource "aws_iam_role" "exec_retail" {
  name               = "${local.prefix}-exec-retail"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
  tags               = { Name = "${local.prefix}-exec-retail" }
}

# ECR 에서 이미지 받기 + CloudWatch 에 로그 쓰기. AWS 가 만들어둔 정책이다
resource "aws_iam_role_policy_attachment" "exec_retail_base" {
  role       = aws_iam_role.exec_retail.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# 자기 DB 비밀번호만 읽는다. 도매 시크릿 ARN 은 여기 없다
data "aws_iam_policy_document" "read_retail_secret" {
  statement {
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [aws_db_instance.retail.master_user_secret[0].secret_arn]
  }
}

resource "aws_iam_role_policy" "exec_retail_secret" {
  name   = "read-retail-db-secret"
  role   = aws_iam_role.exec_retail.id
  policy = data.aws_iam_policy_document.read_retail_secret.json
}

# ── 실행 역할 · 도매 ─────────────────────────────────────────
resource "aws_iam_role" "exec_wholesale" {
  name               = "${local.prefix}-exec-wholesale"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
  tags               = { Name = "${local.prefix}-exec-wholesale" }
}

resource "aws_iam_role_policy_attachment" "exec_wholesale_base" {
  role       = aws_iam_role.exec_wholesale.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "read_wholesale_secret" {
  statement {
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [aws_db_instance.wholesale.master_user_secret[0].secret_arn]
  }
}

resource "aws_iam_role_policy" "exec_wholesale_secret" {
  name   = "read-wholesale-db-secret"
  role   = aws_iam_role.exec_wholesale.id
  policy = data.aws_iam_policy_document.read_wholesale_secret.json
}

# ── 태스크 역할 · 소매 ───────────────────────────────────────
#
# 앱이 직접 AWS 를 부를 때 쓴다. 지금은 부를 데가 없다 —
# 파일을 아직 LocalFileStorage 로 컨테이너 안에 쓴다.
# S3 구현체로 갈아끼울 때 여기에 S3 권한을 붙인다(MUL-77 「배포 전에 닫을 것」).
# 빈 역할이라도 미리 만들어 두면 그때 정책만 더하면 된다.
resource "aws_iam_role" "task_retail" {
  name               = "${local.prefix}-task-retail"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
  tags               = { Name = "${local.prefix}-task-retail" }
}

resource "aws_iam_role" "task_wholesale" {
  name               = "${local.prefix}-task-wholesale"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
  tags               = { Name = "${local.prefix}-task-wholesale" }
}
