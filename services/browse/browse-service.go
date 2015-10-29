/**
# Copyright Google Inc. 2015
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
**/

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
