package main

import (
    "github.com/gin-gonic/gin"
    "net/http"
)

func main() {
    gin.SetMode(gin.ReleaseMode)
    router := gin.Default()

    router.GET("/browse/category/:categoryid", func(c *gin.Context) {
        categoryid := c.Param("categoryid")
        c.JSON(http.StatusOK, gin.H{"data": gin.H{"categoryid": categoryid}})
    })

    router.GET("/browse/product/:productid", func(c *gin.Context) {
        productid := c.Param("productid")
        c.JSON(http.StatusOK, gin.H{"data": gin.H{"productid": productid}})
    })

    router.Run(":8100")
}
