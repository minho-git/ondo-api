terraform {
  # 테라폼 자체의 최소 버전
  required_version = ">= 1.10"

  # AWS API 를 부르는 플러그인. init 이 이걸 내려받는다
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0" # 6.x 는 허용, 7.0 은 막는다
    }
  }

  # state(만든 것의 기록)는 지금 로컬이다.
  # C단계에서 S3 백엔드를 붙이고 -migrate-state 로 옮긴다.
}
