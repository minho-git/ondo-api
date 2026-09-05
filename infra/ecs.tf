# ─────────────────────────────────────────────────────────────
# C · ECS — 앱이 도는 곳
#
# Fargate 라 관리할 EC2 인스턴스가 없다. OS 패치 · SSH · 디스크를 안 본다.
#
# 여기까지가 「띄울 준비」다. 실제로 태스크를 굴리는 서비스는 D 에서 만든다 —
# 서비스는 ALB 타깃그룹에 자기를 등록해야 해서 ALB 가 먼저 있어야 한다.
# ─────────────────────────────────────────────────────────────

# 브라우저에서 API 를 부를 화면 주소. 서버마다 다르다 —
# 소매 API 는 소매 화면만, 도매 API 는 도매 POS 만 받는다.
# 여러 개면 쉼표로 잇는다.
variable "retail_cors_origins" {
  description = "소매 API 를 부를 화면"
  type        = string

  # localhost:3001 은 ⚠️ 임시 개방 (MUL-110). 운영 띄우기 전에 뺀다 — MUL-103.
  # 창은이가 로컬 화면으로 이 배포 API 를 부르며 개발한다. 쿠키도 같이 풀었다
  # (application-prod.yml 의 same-site: none). 둘 중 하나만 있으면 로그인이 안 된다.
  default = "https://ddmondo.co.kr,http://localhost:3001"
}

variable "wholesale_cors_origins" {
  description = "도매 API 를 부를 화면"
  type        = string
  default     = "https://pos.ddmondo.co.kr"
}

resource "aws_ecs_cluster" "main" {
  name = local.prefix

  # 태스크가 어디서 왜 멈췄는지 콘솔에서 볼 수 있게 한다. 공짜다
  setting {
    name  = "containerInsights"
    value = "disabled" # ⚠️ 켜면 CloudWatch 지표 요금이 붙는다. 필요해지면 켠다
  }

  tags = { Name = local.prefix }
}

# ── 로그 ─────────────────────────────────────────────────────
#
# 컨테이너 표준출력이 여기로 간다. Fargate 라 서버에 들어가서 로그 파일을
# 볼 수가 없다 — 로그는 전부 이리로 나온다.
locals {
  log_retention_days = 7 # 개발 환경. 오래 두면 요금이 붙는다
}

resource "aws_cloudwatch_log_group" "retail" {
  name              = "/ecs/${local.prefix}-retail"
  retention_in_days = local.log_retention_days
  tags              = { Name = "${local.prefix}-retail-logs" }
}

resource "aws_cloudwatch_log_group" "wholesale" {
  name              = "/ecs/${local.prefix}-wholesale"
  retention_in_days = local.log_retention_days
  tags              = { Name = "${local.prefix}-wholesale-logs" }
}

# ── 태스크 정의 ──────────────────────────────────────────────
#
# 「이 이미지를 이만한 자원으로, 이 환경변수와 함께 띄워라」는 설계도다.
# 실제로 뜨는 건 D 의 서비스가 이걸 보고 만든다.
#
# 512 CPU / 1024 MB 로 잡았다. Fargate 는 조합이 정해져 있어서 256 CPU 를 쓰면
# 메모리를 512·1024·2048 중에서만 고를 수 있다. 스프링 부트에 512MB 는 빠듯하다.

locals {
  task_cpu    = 512
  task_memory = 1024

  # ECS 가 시크릿을 읽어 환경변수로 넣어준다. 뒤의 `:password::` 는
  # RDS 가 만든 JSON({"username":..,"password":..})에서 password 키만 꺼내라는 뜻이다.
  # 앱은 그냥 환경변수로 받는다 — application-prod.yml 이 이미 그렇게 돼 있다
  retail_secret    = "${aws_db_instance.retail.master_user_secret[0].secret_arn}:password::"
  wholesale_secret = "${aws_db_instance.wholesale.master_user_secret[0].secret_arn}:password::"

  # 소매 접점 시크릿 (MUL-87). 문자열 하나를 통째로 넣은 것이라
  # 위와 달리 뒤에 `:키::` 를 안 붙인다
  gateway_secret = aws_secretsmanager_secret.gateway.arn

  # 소매가 도매를 부르는 주소. HTTP 다 — VPC 안에서만 도는 트래픽이라
  # 인증서를 붙이지 않았다(alb_internal.tf)
  wholesale_base_url = "http://${aws_lb.internal.dns_name}"
}

resource "aws_ecs_task_definition" "retail" {
  family                   = "${local.prefix}-retail"
  requires_compatibilities = ["FARGATE"]

  # 팀 노트북이 셋 다 M 시리즈다. ARM 으로 맞추면 에뮬레이션 없이 빌드하고
  # Fargate 요금도 20% 싸다. 로컬에서 돌린 것과 같은 아키텍처라
  # 「로컬에선 됐는데」가 안 생긴다.
  #
  # ⚠️ GitHub Actions 로 넘어가면 러너 아키텍처를 여기에 맞춰야 한다.
  #    x86 러너에서 빌드한 이미지는 이 태스크에서 안 뜬다.
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "ARM64"
  }
  network_mode = "awsvpc" # Fargate 는 이것만 된다. 태스크마다 자기 IP 를 받는다
  cpu          = local.task_cpu
  memory       = local.task_memory

  execution_role_arn = aws_iam_role.exec_retail.arn
  task_role_arn      = aws_iam_role.task_retail.arn

  container_definitions = jsonencode([{
    name = "retail"

    # ⚠️ 아직 이 이미지가 ECR 에 없다. docker push 를 해야 태스크가 뜬다.
    # 태스크 정의를 만드는 것 자체는 이미지가 없어도 된다
    image = "${aws_ecr_repository.retail.repository_url}:latest"

    essential = true
    portMappings = [{
      containerPort = 8080
      protocol      = "tcp"
    }]

    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
      { name = "DB_URL", value = "jdbc:postgresql://${aws_db_instance.retail.endpoint}/ondo_retail" },
      { name = "DB_USERNAME", value = "ondo" },
      { name = "CORS_ALLOWED_ORIGINS", value = var.retail_cors_origins },
      # 도매를 부르는 주소 (MUL-87). 내부 ALB 다
      { name = "WHOLESALE_BASE_URL", value = local.wholesale_base_url },
    ]

    secrets = [
      { name = "DB_PASSWORD", valueFrom = local.retail_secret },
      # 도매를 부를 때 헤더에 실을 값 (MUL-87)
      { name = "ONDO_GATEWAY_SECRET", valueFrom = local.gateway_secret },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.retail.name
        "awslogs-region"        = "ap-northeast-2"
        "awslogs-stream-prefix" = "ecs"
      }
    }
  }])

  tags = { Name = "${local.prefix}-retail" }
}

resource "aws_ecs_task_definition" "wholesale" {
  family                   = "${local.prefix}-wholesale"
  requires_compatibilities = ["FARGATE"]

  # 팀 노트북이 셋 다 M 시리즈다. ARM 으로 맞추면 에뮬레이션 없이 빌드하고
  # Fargate 요금도 20% 싸다. 로컬에서 돌린 것과 같은 아키텍처라
  # 「로컬에선 됐는데」가 안 생긴다.
  #
  # ⚠️ GitHub Actions 로 넘어가면 러너 아키텍처를 여기에 맞춰야 한다.
  #    x86 러너에서 빌드한 이미지는 이 태스크에서 안 뜬다.
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "ARM64"
  }
  network_mode = "awsvpc"
  cpu          = local.task_cpu
  memory       = local.task_memory

  execution_role_arn = aws_iam_role.exec_wholesale.arn
  task_role_arn      = aws_iam_role.task_wholesale.arn

  container_definitions = jsonencode([{
    name      = "wholesale"
    image     = "${aws_ecr_repository.wholesale.repository_url}:latest"
    essential = true
    portMappings = [{
      containerPort = 8081
      protocol      = "tcp"
    }]

    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
      { name = "DB_URL", value = "jdbc:postgresql://${aws_db_instance.wholesale.endpoint}/ondo_wholesale" },
      { name = "DB_USERNAME", value = "ondo" },
      # ⚠️ 도매는 application-prod.yml 에 CORS 설정이 아직 없다(MUL-86 이 local 에만 넣었다).
      # 채빈이 환경변수를 받게 고치기 전까지 이 값은 무시된다
      { name = "CORS_ALLOWED_ORIGINS", value = var.wholesale_cors_origins },
    ]

    secrets = [
      { name = "DB_PASSWORD", valueFrom = local.wholesale_secret },
      # 소매가 보낸 헤더를 대조할 값 (MUL-87). 없으면 앱이 안 뜬다 —
      # 시크릿을 빠뜨린 채 소매 접점이 열려 있는 것보다 낫다
      { name = "ONDO_GATEWAY_SECRET", valueFrom = local.gateway_secret },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.wholesale.name
        "awslogs-region"        = "ap-northeast-2"
        "awslogs-stream-prefix" = "ecs"
      }
    }
  }])

  tags = { Name = "${local.prefix}-wholesale" }
}
