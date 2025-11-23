add_rules("mode.debug", "mode.release")

add_repositories("liteldev-repo https://github.com/LiteLDev/xmake-repo.git")

-- Good habit to lock version | 锁版本是好习惯
add_requires("levilamina 1.7.5", {configs = {target_type = "server"}}) 
add_requires("levibuildscript 0.4.0")

if not has_config("vs_runtime") then
    set_runtimes("MD")
end

target("Suicide")
    add_rules("@levibuildscript/linkrule")
    add_rules("@levibuildscript/modpacker")
    add_cxflags(
        "/EHa",
        "/utf-8",
        "/W4",
        "/w44265",
        "/w44289",
        "/w44296",
        "/w45263",
        "/w44738",
        "/w45204"
    )
    add_defines(
        "NOMINMAX",
        "UNICODE",
        "_HAS_CXX23=1"
    )
    add_packages(
        "levilamina"
    )
    set_exceptions("none")
    set_kind("shared")
    set_languages("c++20")
    set_symbols("debug")
    add_headerfiles("src/**.h")
    add_files("src/**.cpp")
    add_includedirs("src")
    after_build(function (target)
        -- Here we define a lang_path variable to store the path of the lang folder | 这里定义了一个lang_path变量，用于存储lang文件夹的路径
        local lang_path = path.join(os.projectdir(), "bin", target:name(), "lang")
        -- If the lang folder exists, we delete it | 如果lang文件夹存在，就删除它
        if os.exists(lang_path) then
            os.rmdir(lang_path)
        end
        -- We copy the lang folder to the bin folder | 将lang文件夹复制到bin文件夹中
        os.cp(path.join(os.projectdir(), "lang"), lang_path)
    end)