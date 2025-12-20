package middleware

import (
	"net/http"

	"github.com/gin-gonic/gin"
)

func AuthMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {

		if c.Request.Method == http.MethodOptions {
			c.Next()
			return
		}

		if c.GetHeader("X-Internal-Gateway") != "true" {
			c.AbortWithStatusJSON(403, gin.H{
				"error": "forbidden",
			})
			return
		}

		userID := c.GetHeader("X-User-Id")
		if userID == "" {
			c.AbortWithStatusJSON(401, gin.H{
				"error": "missing user identity",
			})
			return
		}

		role := c.GetHeader("X-User-Role")

		c.Set("userID", userID)
		c.Set("role", role)

		c.Next()
	}
}
