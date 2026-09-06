# ─────────────────────────────────────────────────────────────
# D · ALB — 바깥에서 들어오는 문
#
# 인터넷에서 우리 앱으로 오는 유일한 길이다. 태스크는 프라이빗 서브넷에 있어서
# 직접은 못 닿는다.
#
# ⚠️ 내부 ALB(소매 → 도매)는 여기 없다. 소매가 도매를 부르는 코드가 아직 없어서
#    (MockListingClient 가 하드코딩된 값을 돌려준다) 만들어도 지나갈 트래픽이 0 이다.
#    계획 4(상품·미송 실 연동)에서 실제 클라이언트를 만들 때 같이 붙인다.
# ─────────────────────────────────────────────────────────────

resource "aws_lb" "external" {
  name               = "${local.prefix}-alb"
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.public[*].id

  # 개발 환경이라 지우기 쉽게 둔다. 운영에서는 켠다
  enable_deletion_protection = false

  tags = { Name = "${local.prefix}-alb" }
}

# ── 타깃그룹 — ALB 가 트래픽을 보낼 곳 ────────────────────────
#
# target_type 이 ip 인 건 Fargate 라서다. 태스크마다 자기 IP 를 받으므로
# 인스턴스가 아니라 IP 를 등록한다.
#
# 헬스체크 경로가 /actuator/health/liveness 다. /actuator/health 가 아니다 —
# 그건 DB 까지 본다. ALB 가 보는 주소는 실패하면 ECS 가 태스크를 죽이고 새로
# 띄우는 주소라, 「새로 띄우면 고쳐지는 것」만 들어가야 한다. DB 가 죽은 건
# 새로 띄워도 안 고쳐져서 재시작만 반복한다(MUL-76).

locals {
  # MUL-76 에서 잰 값 — 기동 소매 3.7초 · 도매 2.1~3.2초, 200 까지 5초.
  # 여유를 두되 너무 길면 죽은 태스크를 늦게 빼게 된다
  health_interval = 30
  health_timeout  = 5
  healthy_count   = 2
  unhealthy_count = 3
}

resource "aws_lb_target_group" "retail" {
  name        = "${local.prefix}-retail"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  health_check {
    path                = "/actuator/health/liveness"
    matcher             = "200"
    interval            = local.health_interval
    timeout             = local.health_timeout
    healthy_threshold   = local.healthy_count
    unhealthy_threshold = local.unhealthy_count
  }

  # 태스크를 뺄 때 진행 중인 요청이 끝나기를 기다리는 시간.
  # 기본 300초는 배포가 그만큼 길어진다. 우리 요청은 짧다
  deregistration_delay = 30

  tags = { Name = "${local.prefix}-retail" }
}

resource "aws_lb_target_group" "wholesale" {
  name        = "${local.prefix}-wholesale"
  port        = 8081
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  health_check {
    path                = "/actuator/health/liveness"
    matcher             = "200"
    interval            = local.health_interval
    timeout             = local.health_timeout
    healthy_threshold   = local.healthy_count
    unhealthy_threshold = local.unhealthy_count
  }

  deregistration_delay = 30

  tags = { Name = "${local.prefix}-wholesale" }
}

# ── HTTPS 리스너 ─────────────────────────────────────────────
#
# 기본 동작을 404 로 둔다. 아는 경로만 앱으로 보내고 나머지는 ALB 에서 끊는다 —
# 봇이 쏘는 쓰레기 URL 이 앱까지 안 온다.
resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.external.arn
  port              = 443
  protocol          = "HTTPS"
  certificate_arn   = aws_acm_certificate.api_dev.arn

  # 기본값은 옛 TLS 버전을 허용한다. 1.2 이상만 받는다
  ssl_policy = "ELBSecurityPolicy-TLS13-1-2-2021-06"

  default_action {
    type = "fixed-response"

    fixed_response {
      content_type = "application/json"
      status_code  = "404"
      # 우리 에러 규약과 같은 모양으로 낸다. 프론트가 res.json() 해도 안 터진다
      message_body = "{\"code\":\"RESOURCE_NOT_FOUND\",\"message\":\"요청하신 주소를 찾을 수 없습니다.\",\"errors\":[],\"traceId\":null}"
    }
  }

  tags = { Name = "${local.prefix}-https" }
}

resource "aws_lb_listener_rule" "retail" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 100

  condition {
    path_pattern {
      values = ["/api/retail/*"]
    }
  }

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.retail.arn
  }
}

# ⚠️ 임시 개방 (MUL-110). 운영 띄우기 전에 지운다 — MUL-103.
#
# 창은이가 로컬 화면으로 배포 API 를 부르며 개발하려면 계약을 볼 데가 있어야 한다.
# 스칼라는 API 서버 자기 자신에서 뜨는 화면이라, 여기서 누르면 CORS 도 쿠키 문제도
# 안 생긴다 — 같은 출처끼리라서다.
#
# 여는 순간 우리 API 전체 구조가 인터넷에 공개된다. 지금은 가짜 데이터뿐이라
# 실질 피해가 없지만, 운영 전에 반드시 닫아야 하는 항목이 둘 늘어난 것이다.
#
# 두 앱의 화면 주소는 원래 달랐다.
#
#   소매   /docs        (정적 파일 + 리다이렉트)
#   도매   /docs.html   (Scalar)
#
# 겹친 건 springdoc 이 만드는 JSON 하나뿐이었다. 그래서 소매를
# /v3/api-docs-retail 로 옮겼다(retail-api/application.yml). 도매를 안 옮긴 건
# 채빈 영역이어서다.
resource "aws_lb_listener_rule" "retail_docs" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 150

  condition {
    path_pattern {
      values = ["/docs", "/docs/*", "/v3/api-docs-retail", "/v3/api-docs-retail/*"]
    }
  }

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.retail.arn
  }
}

# ⚠️ 임시 개방 (MUL-110). 위와 같이 운영 전에 지운다 — MUL-103.
#
# 도매 문서는 채빈 영역이라 앱은 안 건드렸다. 주소를 그대로 두고 ALB 에서만 보낸다.
resource "aws_lb_listener_rule" "wholesale_docs" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 160

  condition {
    path_pattern {
      values = ["/docs.html", "/v3/api-docs", "/v3/api-docs/*"]
    }
  }

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.wholesale.arn
  }
}

resource "aws_lb_listener_rule" "wholesale" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 200

  condition {
    path_pattern {
      values = ["/api/wholesale/*"]
    }
  }

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.wholesale.arn
  }
}

# ── HTTP → HTTPS ─────────────────────────────────────────────
# http 주소를 친 사람이 「연결할 수 없음」을 보지 않게 한다
resource "aws_lb_listener" "http_redirect" {
  load_balancer_arn = aws_lb.external.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "redirect"

    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }

  tags = { Name = "${local.prefix}-http-redirect" }
}

# ── DNS — api-dev 를 ALB 로 ──────────────────────────────────
#
# ALB 는 IP 가 고정이 아니라 A 레코드로 직접 못 가리킨다.
# 별칭(alias)은 AWS 안에서 이름으로 이어주는 것이라 IP 가 바뀌어도 따라간다.
resource "aws_route53_record" "api_dev" {
  zone_id = aws_route53_zone.root.zone_id
  name    = "api-dev.ddmondo.co.kr"
  type    = "A"

  alias {
    name                   = aws_lb.external.dns_name
    zone_id                = aws_lb.external.zone_id
    evaluate_target_health = true
  }
}
