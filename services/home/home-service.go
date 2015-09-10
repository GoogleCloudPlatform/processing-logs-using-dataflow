package main

import (
    "github.com/gin-gonic/gin"
    "net/http"
)

func main() {
    gin.SetMode(gin.ReleaseMode)
    router := gin.Default()

    router.GET("/home", func(c *gin.Context) {
        c.JSON(http.StatusOK, gin.H{"data": gin.H{"message": "Hello World!"}})
    })

    router.Run(":8000")
}
