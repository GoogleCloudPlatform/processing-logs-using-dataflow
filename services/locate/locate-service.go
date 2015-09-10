package main

import (
    "github.com/gin-gonic/gin"
    "net/http"
)

func main() {
    gin.SetMode(gin.ReleaseMode)
    router := gin.Default()

    router.GET("/locate/:productid", func(c *gin.Context) {
        productid := c.Param("productid")
        zipcode := c.DefaultQuery("zipcode", "99999")
        c.JSON(http.StatusOK, gin.H{"data": gin.H{"near": zipcode, "productid": productid}})
    })

    router.Run(":8200")
}
