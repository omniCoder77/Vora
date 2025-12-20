package config

import (
	"context"
	"log"
	"sync"
	"sync/atomic"

	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/s3"
)

type S3Client struct {
	Client *s3.Client
	Bucket string
}

// Global S3 client instance
var s3ClientPtr atomic.Pointer[S3Client]

// LoadAWSConfig loads AWS configuration from ~/.aws/credentials automatically
func LoadAWSConfig() *S3Client {
	if client := s3ClientPtr.Load(); client != nil {
		return client
	}

	cfg, err := config.LoadDefaultConfig(context.TODO())
	if err != nil {
		log.Fatalf("Failed to load AWS config: %v", err)
	}

	bucketName := "lynk.bucket"

	client := &S3Client{
		Client: s3.NewFromConfig(cfg),
		Bucket: bucketName,
	}

	s3ClientPtr.Store(client)
	return client
}

var (
	s3Client     *S3Client
	s3ClientOnce sync.Once
)

// GetS3Client returns the singleton S3 client
func GetS3Client() *S3Client {
	s3ClientOnce.Do(func() {
		cfg, err := config.LoadDefaultConfig(context.TODO())
		if err != nil {
			log.Fatalf("Failed to load AWS config: %v", err)
		}

		s3Client = &S3Client{
			Client: s3.NewFromConfig(cfg),
			Bucket: "lynk.bucket",
		}
		log.Println("AWS S3 client initialized")
	})

	return s3Client
}
