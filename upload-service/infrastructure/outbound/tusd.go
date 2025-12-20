package outbound

import (
	"log"
	"upload-service/application/config"

	tusd "github.com/tus/tusd/v2/pkg/handler"
	"github.com/tus/tusd/v2/pkg/s3store"
)

func TusdHandler(s3Client *config.S3Client) *tusd.Handler {
	composer := tusd.NewStoreComposer()

	s3Store := s3store.New(s3Client.Bucket, s3Client.Client)
	s3Store.UseIn(composer)

	handler, err := tusd.NewHandler(tusd.Config{
		BasePath:                "/",
		StoreComposer:           composer,
		NotifyCompleteUploads:   true,
		RespectForwardedHeaders: true,
	})
	if err != nil {
		log.Fatalf("unable to create handler: %s", err)
	}

	return handler
}
