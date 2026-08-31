# apply 가 끝나면 터미널에 찍히는 값들

output "gabia_nameservers" {
  description = "가비아 「네임서버 설정」에 넣을 4개"
  value       = aws_route53_zone.root.name_servers
}

output "hosted_zone_id" {
  description = "앞으로 만들 레코드(api → ALB)가 들어갈 존"
  value       = aws_route53_zone.root.zone_id
}

output "api_certificate_arn" {
  description = "C단계에서 ALB HTTPS 리스너에 붙일 인증서 주소"
  value       = aws_acm_certificate.api.arn
}
