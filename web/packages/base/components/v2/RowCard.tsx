import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import { Box, Stack, Typography } from "@mui/material";
import { FocusVisibleButton } from "ente-base/components/mui/FocusVisibleButton";
import React from "react";

interface RowCardProps {
    title: React.ReactNode;
    subtitle?: string;
    onClick?: () => void;
    endIcon?: React.ReactNode;
}

export const RowCard: React.FC<RowCardProps> = ({
    title,
    subtitle,
    onClick,
    endIcon = <ChevronRightIcon />,
}) => (
    <Box
        component={onClick ? FocusVisibleButton : "div"}
        onClick={onClick}
        sx={[
            {
                display: "flex",
                width: "100%",
                p: "14px 8px 14px 0.5rem",
                gap: 1,
                justifyContent: "space-between",
                alignItems: "center",
                textAlign: "left",
                borderRadius: "10px",
                color: "text.base",
                bgcolor: "transparent",
                "&:hover": { bgcolor: "fill.faintHover" },
                "& .MuiSvgIcon-root": { fontSize: "20px", color: "text.muted" },
            },
            (theme) =>
                theme.applyStyles("dark", {
                    "&:hover": { bgcolor: "backdrop.muted" },
                }),
        ]}
    >
        <Stack sx={{ minWidth: 0 }}>
            <Typography noWrap sx={{ fontWeight: "medium" }}>
                {title}
            </Typography>
            {subtitle && (
                <Typography
                    noWrap
                    variant="small"
                    sx={{
                        color: "text.muted",
                        lineHeight: "20px",
                        fontWeight: 400,
                    }}
                >
                    {subtitle}
                </Typography>
            )}
        </Stack>
        {endIcon}
    </Box>
);
