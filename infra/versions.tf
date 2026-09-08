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

  # ── state 를 두는 곳 (MUL-111) ──────────────────────────────
  #
  # 전에는 로컬이었다. 그래서 이 파일이 곧 비밀이면서 내 노트북에만 있었다 —
  # 노트북이 죽으면 「내가 뭘 만들었는지」가 통째로 사라지고, 팀원은 plan 조차
  # 못 돌리고, CI 도 터라폼을 못 굴렸다(MUL-105 에서 실제로 막혔다).
  #
  # 버킷은 터라폼이 안 만들었다. 만들면 「그 버킷의 state 는 어디 두나」는
  # 순환이 생긴다. 손으로 만들고 태그에 ManagedBy=manual-bootstrap 을 붙여 뒀다.
  #
  #   버전 관리   실수로 덮어써도 이전 state 로 되돌린다
  #   AES256     소매 접점 시크릿(MUL-87) 값이 state 에 평문으로 남는다
  #   퍼블릭 차단  4개 전부
  #
  # key 에 dev/ 를 붙인 건 나중에 운영 환경이 생겼을 때를 위해서다.
  # 같은 버킷에 prod/terraform.tfstate 로 나란히 둔다.
  backend "s3" {
    bucket = "ondo-tfstate-172961885321"
    key    = "dev/terraform.tfstate"
    region = "ap-northeast-2"

    # SSO 프로파일. providers.tf 와 달리 백엔드는 변수를 못 써서 값을 박는다
    profile = "ondo"

    # 잠금. 둘이 동시에 apply 하면 장부가 깨지는 걸 막는다.
    # 예전엔 DynamoDB 테이블이 필요했는데 지금은 S3 만으로 된다 (터라폼 1.10+)
    use_lockfile = true

    encrypt = true
  }
}
