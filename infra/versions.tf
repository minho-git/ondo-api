terraform {
  # 테라폼 자체의 최소 버전
  required_version = ">= 1.10"

  # AWS API 를 부르는 플러그인. init 이 이걸 내려받는다
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0" # 6.x 는 허용, 7.0 은 막는다
    }

    # 소매 접점 시크릿을 만드는 데 쓴다 (MUL-87).
    # 값이 state 에 남는다 — 지금 state 가 로컬이라 이 파일이 곧 비밀이다.
    # S3 백엔드로 옮길 때 암호화를 같이 켜야 한다
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # state(만든 것의 기록)는 지금 로컬이다.
  # C단계에서 S3 백엔드를 붙이고 -migrate-state 로 옮긴다.
}
