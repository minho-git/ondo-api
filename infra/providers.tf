provider "aws" {
  region  = "ap-northeast-2" # 서울
  profile = "ondo"           # ~/.aws/config 의 SSO 프로파일

  # 여기서 만드는 모든 리소스에 자동으로 붙는 태그.
  # 나중에 "이거 누가 만든 거지" 를 콘솔에서 바로 알 수 있다.
  default_tags {
    tags = {
      Project   = "ondo"
      ManagedBy = "terraform"
    }
  }
}
