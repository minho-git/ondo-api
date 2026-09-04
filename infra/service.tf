# ─────────────────────────────────────────────────────────────
# D · ECS 서비스 — 태스크를 실제로 굴리는 것
#
# 태스크 정의가 설계도라면 서비스는 「그 설계도로 항상 2개를 띄워둬라」는 지시다.
# 하나가 죽으면 알아서 다시 띄우고, 배포하면 하나씩 갈아끼운다.
# ─────────────────────────────────────────────────────────────

locals {
  # AZ 마다 하나씩. AZ 장애보다 무중단 배포 때문이 크다 —
  # 하나씩 교체하려면 여분이 있어야 한다. AZ 장애는 드물지만 배포는 매번 한다
  desired_count = 2

  # 앱이 뜨는 동안 헬스체크 실패를 봐주는 시간. MUL-76 에서 잰 값이
  # 기동 3.7초 · 200 까지 5초라 60초면 넉넉하다. Flyway 가 마이그레이션을
  # 돌리는 것까지 포함된 값이다
  health_grace = 60
}

resource "aws_ecs_service" "retail" {
  name            = "${local.prefix}-retail"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.retail.arn
  desired_count   = local.desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets         = aws_subnet.app[*].id
    security_groups = [aws_security_group.app_retail.id]

    # 프라이빗 서브넷이라 공인 IP 를 안 준다. 밖으로는 NAT 로 나간다
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.retail.arn
    container_name   = "retail"
    container_port   = 8080
  }

  health_check_grace_period_seconds = local.health_grace

  # 배포가 실패하면 자동으로 되돌린다. 없으면 깨진 이미지를 올렸을 때
  # 태스크가 계속 죽었다 살았다 하면서 서비스가 안 뜬 채로 남는다
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  # 리스너 규칙이 먼저 있어야 타깃그룹이 ALB 에 실제로 물린다
  depends_on = [aws_lb_listener_rule.retail]

  tags = { Name = "${local.prefix}-retail" }
}

resource "aws_ecs_service" "wholesale" {
  name            = "${local.prefix}-wholesale"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.wholesale.arn
  desired_count   = local.desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.app[*].id
    security_groups  = [aws_security_group.app_wholesale.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.wholesale.arn
    container_name   = "wholesale"
    container_port   = 8081
  }

  # 내부 ALB 에도 자기를 등록한다 (MUL-87). 타깃그룹 하나는 로드밸런서 하나에만
  # 붙으므로, 같은 태스크를 두 타깃그룹에 넣는 방식으로 두 ALB 를 받는다
  load_balancer {
    target_group_arn = aws_lb_target_group.wholesale_internal.arn
    container_name   = "wholesale"
    container_port   = 8081
  }

  health_check_grace_period_seconds = local.health_grace

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  depends_on = [aws_lb_listener_rule.wholesale, aws_lb_listener_rule.internal_retail_gateway]

  tags = { Name = "${local.prefix}-wholesale" }
}
