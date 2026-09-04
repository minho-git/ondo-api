# ─────────────────────────────────────────────────────────────
# B · 보안그룹 — 누가 누구한테 말을 걸 수 있나
#
# 라우팅이 「길이 있나」를 정하고, 여기가 「그 문이 열려 있나」를 정한다.
# 서브넷이 달라도 VPC 안이면 길은 이미 있다(local 경로). 실제로 막는 건 여기다.
#
# 보안그룹은 기본이 전부 잠김이다. 아래 적은 것만 열린다.
# 그리고 오간 걸 기억해서, 들어온 요청의 응답은 규칙 없이도 나간다.
#
# 출처를 IP 대역이 아니라 「다른 보안그룹」으로 지목한다. 앱이 IP 를 바꿔 떠도
# 규칙을 안 고쳐도 되고, 규칙만 읽어도 누가 누구를 부르는지 그림이 보인다.
# ─────────────────────────────────────────────────────────────

# ── 외부 ALB ─────────────────────────────────────────────────
# 인터넷에서 들어오는 유일한 문. 도매 POS 와 소매 화면이 둘 다 여기로 들어온다
resource "aws_security_group" "alb" {
  name        = "${local.prefix}-alb"
  description = "External ALB - public HTTPS entry"
  vpc_id      = aws_vpc.main.id
  tags        = { Name = "${local.prefix}-alb" }
}

resource "aws_vpc_security_group_ingress_rule" "alb_https" {
  security_group_id = aws_security_group.alb.id
  description       = "HTTPS from anywhere"
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}

# http 로 들어온 걸 https 로 돌려보내려면 80 도 받아야 한다.
# 안 열면 http 주소를 친 사람이 「연결할 수 없음」을 본다
resource "aws_vpc_security_group_ingress_rule" "alb_http" {
  security_group_id = aws_security_group.alb.id
  description       = "HTTP from anywhere - redirected to HTTPS"
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 80
  to_port           = 80
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "alb_to_retail" {
  security_group_id            = aws_security_group.alb.id
  description                  = "To retail tasks"
  referenced_security_group_id = aws_security_group.app_retail.id
  from_port                    = 8080
  to_port                      = 8080
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "alb_to_wholesale" {
  security_group_id            = aws_security_group.alb.id
  description                  = "To wholesale tasks"
  referenced_security_group_id = aws_security_group.app_wholesale.id
  from_port                    = 8081
  to_port                      = 8081
  ip_protocol                  = "tcp"
}

# ── 소매 앱 ──────────────────────────────────────────────────
resource "aws_security_group" "app_retail" {
  name        = "${local.prefix}-app-retail"
  description = "Retail ECS tasks"
  vpc_id      = aws_vpc.main.id
  tags        = { Name = "${local.prefix}-app-retail" }
}

# ALB 를 거친 것만 받는다. 인터넷에서 태스크로 직접은 못 온다
resource "aws_vpc_security_group_ingress_rule" "app_retail_from_alb" {
  security_group_id            = aws_security_group.app_retail.id
  description                  = "From ALB only"
  referenced_security_group_id = aws_security_group.alb.id
  from_port                    = 8080
  to_port                      = 8080
  ip_protocol                  = "tcp"
}

# 나가는 건 열어둔다 — NAT 를 거쳐 ECR 에서 이미지를 받고 Secrets Manager 를 부른다.
# 목적지를 좁히려면 AWS 서비스마다 IP 대역을 따라다녀야 해서 실익이 없다
resource "aws_vpc_security_group_egress_rule" "app_retail_out" {
  security_group_id = aws_security_group.app_retail.id
  description       = "ECR - Secrets Manager - wholesale API"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

# ── 도매 앱 ──────────────────────────────────────────────────
resource "aws_security_group" "app_wholesale" {
  name        = "${local.prefix}-app-wholesale"
  description = "Wholesale ECS tasks"
  vpc_id      = aws_vpc.main.id
  tags        = { Name = "${local.prefix}-app-wholesale" }
}

# ── 내부 ALB (MUL-87) ────────────────────────────────────────
#
# 소매가 도매를 부르는 길이다. 공개 ALB 와 달리 인터넷에서 안 온다.
# 소매 태스크 SG 에서만 받는다 — VPC 안 다른 무언가가 도매 데이터를 긁어가지 못하게.
resource "aws_security_group" "alb_internal" {
  name        = "${local.prefix}-alb-internal"
  description = "Internal ALB - retail to wholesale"
  vpc_id      = aws_vpc.main.id
  tags        = { Name = "${local.prefix}-alb-internal" }
}

resource "aws_vpc_security_group_ingress_rule" "alb_internal_from_retail" {
  security_group_id            = aws_security_group.alb_internal.id
  description                  = "From retail tasks only"
  referenced_security_group_id = aws_security_group.app_retail.id
  from_port                    = 80
  to_port                      = 80
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "alb_internal_to_wholesale" {
  security_group_id            = aws_security_group.alb_internal.id
  description                  = "To wholesale tasks"
  referenced_security_group_id = aws_security_group.app_wholesale.id
  from_port                    = 8081
  to_port                      = 8081
  ip_protocol                  = "tcp"
}

# 도매는 공개 ALB(도매 화면)와 내부 ALB(소매 접점) 둘 다에서 받는다.
# 규칙을 나눠 두면 콘솔에서 어느 길이 열려 있는지 한눈에 보인다
resource "aws_vpc_security_group_ingress_rule" "app_wholesale_from_alb" {
  security_group_id            = aws_security_group.app_wholesale.id
  description                  = "From ALB only"
  referenced_security_group_id = aws_security_group.alb.id
  from_port                    = 8081
  to_port                      = 8081
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "app_wholesale_from_alb_internal" {
  security_group_id            = aws_security_group.app_wholesale.id
  description                  = "From internal ALB - retail gateway"
  referenced_security_group_id = aws_security_group.alb_internal.id
  from_port                    = 8081
  to_port                      = 8081
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "app_wholesale_out" {
  security_group_id = aws_security_group.app_wholesale.id
  description       = "ECR - Secrets Manager"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

# ── DB — 도매·소매를 따로 둔다 ────────────────────────────────
#
# 「DB 를 나눈 경계」가 여기서 한 번 더 걸린다. 소매 앱은 도매 DB 에 연결조차
# 못 한다 — 비밀번호를 안다 해도 문이 안 열린다.
#
# 나가는 규칙을 하나도 안 적었다. 테라폼은 기본 전체 허용 규칙을 지우므로
# 이 DB 들은 스스로 어디로도 못 나간다. 들어온 요청의 응답만 나간다.

resource "aws_security_group" "db_retail" {
  name        = "${local.prefix}-db-retail"
  description = "Retail RDS"
  vpc_id      = aws_vpc.main.id
  tags        = { Name = "${local.prefix}-db-retail" }
}

resource "aws_vpc_security_group_ingress_rule" "db_retail_from_app" {
  security_group_id            = aws_security_group.db_retail.id
  description                  = "PostgreSQL from retail tasks only"
  referenced_security_group_id = aws_security_group.app_retail.id
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
}

resource "aws_security_group" "db_wholesale" {
  name        = "${local.prefix}-db-wholesale"
  description = "Wholesale RDS"
  vpc_id      = aws_vpc.main.id
  tags        = { Name = "${local.prefix}-db-wholesale" }
}

resource "aws_vpc_security_group_ingress_rule" "db_wholesale_from_app" {
  security_group_id            = aws_security_group.db_wholesale.id
  description                  = "PostgreSQL from wholesale tasks only"
  referenced_security_group_id = aws_security_group.app_wholesale.id
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
}
