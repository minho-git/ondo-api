# ─────────────────────────────────────────────────────────────
# D-2 · 내부 ALB — 소매가 도매를 부르는 길 (MUL-87)
#
# 소매 태스크는 도매 태스크를 직접 못 부른다. 도매 보안그룹이 「ALB 에서만」
# 받게 돼 있어서다(security.tf). 그래서 VPC 안에만 있는 ALB 를 하나 더 둔다.
#
# 왜 ALB 인가 — 서비스 디스커버리(Cloud Map)나 Service Connect 로도 되지만,
# 이미 있는 구조(ALB 뒤에 서비스)와 같은 모양이라 새로 배울 게 없다.
#
# ⚠️ 타깃그룹은 기존 것을 못 쓴다. 하나의 타깃그룹은 로드밸런서 하나에만 붙는다
#    (TargetGroupAssociationLimit). 그래서 같은 도매 태스크를 가리키는 타깃그룹을
#    따로 만들고, ECS 서비스가 양쪽에 자기를 등록한다(service.tf).
#
# ⚠️ 이걸 만드는 순간 도매의 /api/retail-gateway/** 가 VPC 안에서 닿게 된다.
#    지금까지는 공개 ALB 가 그 경로를 라우팅하지 않아 안 뚫렸을 뿐이다.
#    그래서 같은 작업에서 시크릿 헤더 검사를 붙였다(GatewaySecretAuthorizationManager).
# ─────────────────────────────────────────────────────────────

resource "aws_lb" "internal" {
  name               = "${local.prefix}-alb-internal"
  load_balancer_type = "application"
  internal           = true
  security_groups    = [aws_security_group.alb_internal.id]

  # app 서브넷이다. 퍼블릭에 두면 internal=true 여도 굳이 인터넷 쪽에 붙는 셈이다
  subnets = aws_subnet.app[*].id

  enable_deletion_protection = false

  tags = { Name = "${local.prefix}-alb-internal" }
}

# ── 리스너 ───────────────────────────────────────────────────
#
# HTTP 다. VPC 안에서만 도는 트래픽이라 인증서를 붙이지 않는다 —
# 붙이려면 내부용 사설 인증서를 따로 만들어야 하고, 지금 그 값어치가 없다.
# 대신 소매 SG 에서만 들어오게 막고, 그 위에 시크릿 헤더를 얹었다.
#
# 기본 동작은 공개 ALB 와 같은 404 다. 아는 경로만 도매로 보낸다.
resource "aws_lb_listener" "internal_http" {
  load_balancer_arn = aws_lb.internal.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "fixed-response"

    fixed_response {
      content_type = "application/json"
      status_code  = "404"
      message_body = "{\"code\":\"RESOURCE_NOT_FOUND\",\"message\":\"요청하신 주소를 찾을 수 없습니다.\",\"errors\":[],\"traceId\":null}"
    }
  }

  tags = { Name = "${local.prefix}-internal-http" }
}

# 소매 접점만 연다. 도매 화면용 /api/wholesale/* 는 여기로 안 보낸다 —
# 그건 사람이 브라우저로 쓰는 것이고 공개 ALB 로 이미 간다.
# 내부 ALB 전용 타깃그룹. 설정은 공개 ALB 쪽과 같다 — 같은 태스크를 가리킨다
resource "aws_lb_target_group" "wholesale_internal" {
  name        = "${local.prefix}-wholesale-int"
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

  tags = { Name = "${local.prefix}-wholesale-int" }
}

resource "aws_lb_listener_rule" "internal_retail_gateway" {
  listener_arn = aws_lb_listener.internal_http.arn
  priority     = 10

  condition {
    path_pattern {
      values = ["/api/retail-gateway/*"]
    }
  }

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.wholesale_internal.arn
  }

  tags = { Name = "${local.prefix}-internal-retail-gateway" }
}
