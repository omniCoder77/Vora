package main

import (
	"log"
	"net/http"
	"upload-service/application/config"
	"upload-service/infrastructure/middleware"
	"upload-service/infrastructure/outbound"

	"github.com/gin-gonic/gin"
)

func main() {
	s3Client := config.GetS3Client()
	tusHandler := outbound.TusdHandler(s3Client)

	r := gin.Default()

	basePath := "/files/"

	stripPrefixWrapper := func(h http.Handler) gin.HandlerFunc {
		return func(c *gin.Context) {
			c.Request.Header.Set("X-Forwarded-Prefix", basePath)

			http.StripPrefix(basePath, h).ServeHTTP(c.Writer, c.Request)
		}
	}

	r.Any(basePath+"*path", middleware.AuthMiddleware(), stripPrefixWrapper(tusHandler))

	log.Println("Server starting on :8080")
	if err := r.Run(":8080"); err != nil {
		log.Fatalf("unable to start server: %s", err)
	}
}
